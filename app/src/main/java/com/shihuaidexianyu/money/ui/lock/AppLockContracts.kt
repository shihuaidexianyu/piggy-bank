package com.shihuaidexianyu.money.ui.lock

import com.shihuaidexianyu.money.data.migration.StartupMigrationState

sealed interface AppLockState {
    data object Loading : AppLockState
    data object Locked : AppLockState
    data object Authenticating : AppLockState
    data object Unlocked : AppLockState
    data class Unavailable(val reason: AppLockUnavailableReason) : AppLockState
}

enum class AppLockUnavailableReason {
    NO_HARDWARE,
    NOT_ENROLLED,
    TEMPORARILY_UNAVAILABLE,
    PREFERENCES_UNAVAILABLE,
}

enum class BiometricCapability {
    AVAILABLE,
    NO_HARDWARE,
    NOT_ENROLLED,
    TEMPORARILY_UNAVAILABLE,
}

sealed interface BiometricAuthenticationResult {
    data object Succeeded : BiometricAuthenticationResult
    data object Failed : BiometricAuthenticationResult
    data object Cancelled : BiometricAuthenticationResult
    data class Error(val message: String) : BiometricAuthenticationResult
}

interface BiometricAuthenticationGateway {
    suspend fun capability(): BiometricCapability

    fun authenticate(
        requestToken: Long,
        callback: (Long, BiometricAuthenticationResult) -> Unit,
    )

    fun cancelAuthentication() = Unit
}

fun interface ElapsedRealtimeClock {
    fun nowMillis(): Long
}

enum class AppRootSurface {
    LOCK,
    STARTUP,
    LEDGER,
}

fun resolveAppRootSurface(
    startupState: StartupMigrationState,
    lockState: AppLockState,
): AppRootSurface = when {
    lockState != AppLockState.Unlocked -> AppRootSurface.LOCK
    startupState == StartupMigrationState.Ready -> AppRootSurface.LEDGER
    else -> AppRootSurface.STARTUP
}
