package com.shihuaidexianyu.money.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppLockViewModel(
    private val preferencesRepository: DevicePreferencesRepository,
    private val biometricGateway: BiometricAuthenticationGateway,
    private val elapsedRealtimeClock: ElapsedRealtimeClock,
    private val onPrivacyDefaultsEnabled: () -> Unit = {},
    private val onPrivacyDefaultsEnabling: () -> Unit = {},
    private val onPrivacyDefaultsEnableFailed: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<AppLockState>(AppLockState.Loading)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private var biometricEnabled = false
    private var relockDelay = AppRelockDelay.THIRTY_SECONDS
    private var backgroundedAt: Long? = null
    private var generation = 0L
    private var authenticationEnablesLock = false
    private var enablePersistenceInFlight = false
    private var automaticAuthenticationAttemptedForCurrentLock = false

    init {
        viewModelScope.launch {
            runCatching { preferencesRepository.query() }
                .onSuccess { preferences ->
                    biometricEnabled = preferences.biometricLock
                    relockDelay = preferences.relockDelay
                    if (!biometricEnabled) {
                        _state.value = AppLockState.Unlocked
                    } else {
                        _state.value = capabilityState(biometricGateway.capability())
                    }
                }
                .onFailure {
                    _state.value = AppLockState.Unavailable(
                        AppLockUnavailableReason.PREFERENCES_UNAVAILABLE,
                    )
                }
        }
    }

    fun authenticate() {
        if (!biometricEnabled) return
        automaticAuthenticationAttemptedForCurrentLock = true
        checkCapabilityAndAuthenticate(enablesLock = false)
    }

    fun authenticateAutomaticallyOnce() {
        if (
            !biometricEnabled ||
            _state.value != AppLockState.Locked ||
            automaticAuthenticationAttemptedForCurrentLock
        ) {
            return
        }
        automaticAuthenticationAttemptedForCurrentLock = true
        checkCapabilityAndAuthenticate(enablesLock = false)
    }

    fun authenticateAutomaticallyForForeground() {
        if (!biometricEnabled || _state.value != AppLockState.Locked) return
        automaticAuthenticationAttemptedForCurrentLock = false
        authenticateAutomaticallyOnce()
    }

    fun authenticateForEnable() {
        checkCapabilityAndAuthenticate(enablesLock = true)
    }

    fun enableBiometricLock() {
        if (biometricEnabled) return
        checkCapabilityAndAuthenticate(enablesLock = true)
    }

    fun disableBiometricLock() {
        invalidateAuthentication()
        viewModelScope.launch {
            runCatching { preferencesRepository.updateBiometricLock(false) }
                .onSuccess {
                    biometricEnabled = false
                    authenticationEnablesLock = false
                    _state.value = AppLockState.Unlocked
                }
                .onFailure {
                    _state.value = AppLockState.Unavailable(
                        AppLockUnavailableReason.PREFERENCES_UNAVAILABLE,
                    )
                }
        }
    }

    fun onRelockDelayChanged(delay: AppRelockDelay) {
        relockDelay = delay
    }

    fun onBackgrounded() {
        if (!biometricEnabled && !authenticationEnablesLock && !enablePersistenceInFlight) return
        val wasPendingEnable = authenticationEnablesLock && !biometricEnabled && !enablePersistenceInFlight
        backgroundedAt = elapsedRealtimeClock.nowMillis()
        val authenticationWasActive = _state.value == AppLockState.Authenticating
        invalidateAuthentication()
        authenticationEnablesLock = false
        if (enablePersistenceInFlight) {
            enterLockedState()
            return
        }
        if (wasPendingEnable) {
            _state.value = AppLockState.Unlocked
            return
        }
        if (relockDelay == AppRelockDelay.IMMEDIATELY || authenticationWasActive) {
            enterLockedState()
        }
    }

    fun onForegrounded() {
        if (!biometricEnabled || _state.value != AppLockState.Unlocked) return
        val startedAt = backgroundedAt ?: return
        backgroundedAt = null
        val now = elapsedRealtimeClock.nowMillis()
        val threshold = relockDelay.thresholdMillis()
        if (now < startedAt || now - startedAt >= threshold) {
            lockNow()
        }
    }

    fun onScreenOff() {
        if (enablePersistenceInFlight) {
            invalidateAuthentication()
            authenticationEnablesLock = false
            enterLockedState()
        } else if (!biometricEnabled && authenticationEnablesLock) {
            invalidateAuthentication()
            authenticationEnablesLock = false
            _state.value = AppLockState.Unlocked
        } else if (biometricEnabled) {
            lockNow()
        }
    }

    private fun checkCapabilityAndAuthenticate(enablesLock: Boolean) {
        authenticationEnablesLock = enablesLock
        val capabilityToken = nextGeneration()
        viewModelScope.launch {
            val capability = biometricGateway.capability()
            if (capabilityToken != generation) return@launch
            when (capability) {
                BiometricCapability.AVAILABLE -> startAuthentication(enablesLock)
                else -> {
                    invalidateAuthentication()
                    val failedBeforeEnable = enablesLock && !biometricEnabled
                    authenticationEnablesLock = false
                    _state.value = if (failedBeforeEnable) {
                        AppLockState.Unlocked
                    } else {
                        capabilityState(capability)
                    }
                }
            }
        }
    }

    private fun startAuthentication(enablesLock: Boolean) {
        authenticationEnablesLock = enablesLock
        val requestToken = nextGeneration()
        _state.value = AppLockState.Authenticating
        biometricGateway.authenticate(requestToken) { callbackToken, result ->
            viewModelScope.launch {
                if (callbackToken != generation || _state.value != AppLockState.Authenticating) {
                    return@launch
                }
                when (result) {
                    BiometricAuthenticationResult.Succeeded -> authenticationSucceeded(
                        enablesLock = enablesLock,
                        requestToken = callbackToken,
                    )
                    BiometricAuthenticationResult.Cancelled,
                    BiometricAuthenticationResult.Failed,
                    is BiometricAuthenticationResult.Error,
                    -> {
                        val failedBeforeEnable = authenticationEnablesLock && !biometricEnabled
                        authenticationEnablesLock = false
                        _state.value = if (failedBeforeEnable) {
                            AppLockState.Unlocked
                        } else {
                            AppLockState.Locked
                        }
                    }
                }
            }
        }
    }

    private suspend fun authenticationSucceeded(enablesLock: Boolean, requestToken: Long) {
        if (enablesLock) {
            runCatching(onPrivacyDefaultsEnabling)
            enablePersistenceInFlight = true
            val persisted = runCatching {
                preferencesRepository.enableBiometricLockWithPrivacyDefaults()
            }.isSuccess
            enablePersistenceInFlight = false
            if (persisted) {
                biometricEnabled = true
                runCatching(onPrivacyDefaultsEnabled)
            }
            authenticationEnablesLock = false
            if (requestToken != generation || _state.value != AppLockState.Authenticating) {
                if (persisted) {
                    enterLockedState()
                } else {
                    _state.value = AppLockState.Unlocked
                }
                if (!persisted) runCatching(onPrivacyDefaultsEnableFailed)
                return
            }
            if (!persisted) {
                runCatching(onPrivacyDefaultsEnableFailed)
                _state.value = AppLockState.Unlocked
                return
            }
        }
        authenticationEnablesLock = false
        backgroundedAt = null
        _state.value = AppLockState.Unlocked
    }

    private fun lockNow() {
        invalidateAuthentication()
        enterLockedState()
    }

    private fun enterLockedState() {
        automaticAuthenticationAttemptedForCurrentLock = false
        _state.value = AppLockState.Locked
    }

    private fun invalidateAuthentication() {
        nextGeneration()
        biometricGateway.cancelAuthentication()
    }

    private fun nextGeneration(): Long {
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        return generation
    }

    private fun capabilityState(capability: BiometricCapability): AppLockState = when (capability) {
        BiometricCapability.AVAILABLE -> AppLockState.Locked
        BiometricCapability.NO_HARDWARE -> AppLockState.Unavailable(AppLockUnavailableReason.NO_HARDWARE)
        BiometricCapability.NOT_ENROLLED -> AppLockState.Unavailable(AppLockUnavailableReason.NOT_ENROLLED)
        BiometricCapability.TEMPORARILY_UNAVAILABLE -> AppLockState.Unavailable(
            AppLockUnavailableReason.TEMPORARILY_UNAVAILABLE,
        )
    }
}

private fun AppRelockDelay.thresholdMillis(): Long = when (this) {
    AppRelockDelay.IMMEDIATELY -> 0L
    AppRelockDelay.THIRTY_SECONDS -> 30_000L
    AppRelockDelay.ONE_MINUTE -> 60_000L
    AppRelockDelay.FIVE_MINUTES -> 300_000L
}
