package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.ui.lock.AppLockState
import com.shihuaidexianyu.money.ui.lock.AppLockUnavailableReason
import com.shihuaidexianyu.money.ui.lock.AppLockViewModel
import com.shihuaidexianyu.money.ui.lock.BiometricAuthenticationGateway
import com.shihuaidexianyu.money.ui.lock.BiometricAuthenticationResult
import com.shihuaidexianyu.money.ui.lock.BiometricCapability
import com.shihuaidexianyu.money.ui.lock.ElapsedRealtimeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `process starts loading and disabled preference unlocks only after loading`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = false)

        assertEquals(AppLockState.Loading, fixture.viewModel.state.value)
        advanceUntilIdle()

        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
    }

    @Test
    fun `enabled lock fails closed when biometric capability is unavailable`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = true, capability = BiometricCapability.NOT_ENROLLED)

        advanceUntilIdle()

        assertEquals(
            AppLockState.Unavailable(AppLockUnavailableReason.NOT_ENROLLED),
            fixture.viewModel.state.value,
        )
        assertTrue(fixture.preferences.query().biometricLock)
    }

    @Test
    fun `cancel failure and error remain locked while success alone unlocks`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = true)
        advanceUntilIdle()
        assertEquals(AppLockState.Locked, fixture.viewModel.state.value)

        for (result in listOf(
            BiometricAuthenticationResult.Cancelled,
            BiometricAuthenticationResult.Failed,
            BiometricAuthenticationResult.Error("sensor error"),
        )) {
            fixture.viewModel.authenticate()
            advanceUntilIdle()
            assertEquals(AppLockState.Authenticating, fixture.viewModel.state.value)
            fixture.gateway.complete(result)
            advanceUntilIdle()
            assertEquals(AppLockState.Locked, fixture.viewModel.state.value)
        }

        fixture.viewModel.authenticate()
        advanceUntilIdle()
        fixture.gateway.complete(BiometricAuthenticationResult.Succeeded)
        advanceUntilIdle()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
    }

    @Test
    fun `enabling lock persists only after capability and successful authentication`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = false)
        advanceUntilIdle()

        fixture.viewModel.enableBiometricLock()
        advanceUntilIdle()
        assertEquals(AppLockState.Authenticating, fixture.viewModel.state.value)
        assertFalse(fixture.preferences.query().biometricLock)

        fixture.gateway.complete(BiometricAuthenticationResult.Cancelled)
        advanceUntilIdle()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
        assertFalse(fixture.preferences.query().biometricLock)

        fixture.viewModel.enableBiometricLock()
        advanceUntilIdle()
        fixture.gateway.complete(BiometricAuthenticationResult.Succeeded)
        advanceUntilIdle()
        val enabledPreferences = fixture.preferences.query()
        assertTrue(enabledPreferences.biometricLock)
        assertTrue(enabledPreferences.hideRecentTasks)
        assertTrue(enabledPreferences.hideWidgetAmounts)
        assertTrue(enabledPreferences.hideNotificationAmounts)
        assertFalse(enabledPreferences.maskAmountsInApp)
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
    }

    @Test
    fun `enable capability failure never persists and returns to unlocked settings`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = false, capability = BiometricCapability.NO_HARDWARE)
        advanceUntilIdle()

        fixture.viewModel.enableBiometricLock()
        advanceUntilIdle()

        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
        assertFalse(fixture.preferences.query().biometricLock)
    }

    @Test
    fun `screen off invalidates a suspended capability check before prompt starts`() =
        runTest(dispatcher) {
            val fixture = Fixture(enabled = true)
            advanceUntilIdle()
            val capabilityGate = CompletableDeferred<Unit>()
            fixture.gateway.capabilityGate = capabilityGate

            fixture.viewModel.authenticate()
            runCurrent()
            fixture.viewModel.onScreenOff()
            capabilityGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AppLockState.Locked, fixture.viewModel.state.value)
            assertEquals(0L, fixture.gateway.lastToken)
        }

    @Test
    fun `screen off during first enable returns to settings and can retry`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = false)
        advanceUntilIdle()
        fixture.viewModel.enableBiometricLock()
        advanceUntilIdle()
        assertEquals(AppLockState.Authenticating, fixture.viewModel.state.value)

        fixture.viewModel.onScreenOff()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
        fixture.viewModel.enableBiometricLock()
        advanceUntilIdle()

        assertEquals(AppLockState.Authenticating, fixture.viewModel.state.value)
    }

    @Test
    fun `screen off during suspended enable persistence cannot be overwritten by stale success`() =
        runTest(dispatcher) {
            val persistenceGate = CompletableDeferred<Unit>()
            val preferences = InMemoryDevicePreferencesRepository(
                initial = DevicePreferences(biometricLock = false),
                beforeEnableBiometricLock = { persistenceGate.await() },
            )
            val fixture = Fixture(preferences = preferences)
            advanceUntilIdle()
            fixture.viewModel.enableBiometricLock()
            advanceUntilIdle()
            fixture.gateway.complete(BiometricAuthenticationResult.Succeeded)
            runCurrent()

            fixture.viewModel.onScreenOff()
            assertEquals(AppLockState.Locked, fixture.viewModel.state.value)
            persistenceGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AppLockState.Locked, fixture.viewModel.state.value)
            assertTrue(preferences.query().biometricLock)
        }

    @Test
    fun `enable persistence failure returns unlocked and invokes privacy recovery`() =
        runTest(dispatcher) {
            var recoveryCalls = 0
            val preferences = InMemoryDevicePreferencesRepository(
                initial = DevicePreferences(biometricLock = false),
                beforeEnableBiometricLock = { error("write failed") },
            )
            val gateway = FakeBiometricGateway(BiometricCapability.AVAILABLE)
            val viewModel = AppLockViewModel(
                preferencesRepository = preferences,
                biometricGateway = gateway,
                elapsedRealtimeClock = MutableElapsedClock(),
                onPrivacyDefaultsEnableFailed = { recoveryCalls++ },
            )
            advanceUntilIdle()
            viewModel.enableBiometricLock()
            advanceUntilIdle()
            gateway.complete(BiometricAuthenticationResult.Succeeded)
            advanceUntilIdle()

            assertEquals(AppLockState.Unlocked, viewModel.state.value)
            assertFalse(preferences.query().biometricLock)
            assertEquals(1, recoveryCalls)
        }

    @Test
    fun `elapsed background duration applies every configured relock delay`() = runTest(dispatcher) {
        val cases = listOf(
            AppRelockDelay.IMMEDIATELY to 0L,
            AppRelockDelay.THIRTY_SECONDS to 30_000L,
            AppRelockDelay.ONE_MINUTE to 60_000L,
            AppRelockDelay.FIVE_MINUTES to 300_000L,
        )
        for ((delay, threshold) in cases) {
            val fixture = Fixture(enabled = true, relockDelay = delay)
            unlock(fixture)
            fixture.clock.now = 1_000L
            fixture.viewModel.onBackgrounded()
            if (threshold > 0L) {
                fixture.clock.now += threshold - 1L
                fixture.viewModel.onForegrounded()
                assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
                fixture.viewModel.onBackgrounded()
                fixture.clock.now += threshold
            }
            fixture.viewModel.onForegrounded()
            assertEquals(AppLockState.Locked, fixture.viewModel.state.value, "delay=$delay")
        }
    }

    @Test
    fun `screen off locks immediately and stale success cannot unlock newer generation`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = true)
        advanceUntilIdle()
        fixture.viewModel.authenticate()
        advanceUntilIdle()
        val staleToken = fixture.gateway.lastToken

        fixture.viewModel.onScreenOff()
        assertEquals(AppLockState.Locked, fixture.viewModel.state.value)
        assertTrue(fixture.gateway.cancelCalls > 0)
        fixture.viewModel.authenticate()
        advanceUntilIdle()
        val currentToken = fixture.gateway.lastToken
        assertTrue(currentToken > staleToken)

        fixture.gateway.completeToken(staleToken, BiometricAuthenticationResult.Succeeded)
        advanceUntilIdle()
        assertEquals(AppLockState.Authenticating, fixture.viewModel.state.value)
        fixture.gateway.completeToken(currentToken, BiometricAuthenticationResult.Succeeded)
        advanceUntilIdle()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
    }

    @Test
    fun `every background event advances generation even before relock deadline`() = runTest(dispatcher) {
        val fixture = Fixture(enabled = true, relockDelay = AppRelockDelay.FIVE_MINUTES)
        unlock(fixture)
        val firstAuthenticationToken = fixture.gateway.lastToken
        fixture.clock.now = 10_000L

        fixture.viewModel.onBackgrounded()
        fixture.clock.now = 10_001L
        fixture.viewModel.onForegrounded()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
        fixture.viewModel.onScreenOff()
        fixture.viewModel.authenticate()
        advanceUntilIdle()

        assertTrue(fixture.gateway.lastToken >= firstAuthenticationToken + 3L)
    }

    @Test
    fun `new process never restores unlocked state`() = runTest(dispatcher) {
        val preferences = InMemoryDevicePreferencesRepository(DevicePreferences(biometricLock = true))
        val first = Fixture(preferences = preferences)
        unlock(first)
        assertEquals(AppLockState.Unlocked, first.viewModel.state.value)

        val recreated = Fixture(preferences = preferences)
        assertEquals(AppLockState.Loading, recreated.viewModel.state.value)
        advanceUntilIdle()
        assertEquals(AppLockState.Locked, recreated.viewModel.state.value)
    }

    private inner class Fixture(
        enabled: Boolean = true,
        relockDelay: AppRelockDelay = AppRelockDelay.THIRTY_SECONDS,
        capability: BiometricCapability = BiometricCapability.AVAILABLE,
        val preferences: InMemoryDevicePreferencesRepository = InMemoryDevicePreferencesRepository(
            DevicePreferences(biometricLock = enabled, relockDelay = relockDelay),
        ),
    ) {
        val clock = MutableElapsedClock()
        val gateway = FakeBiometricGateway(capability)
        val viewModel = AppLockViewModel(preferences, gateway, clock)

    }

    private suspend fun TestScope.unlock(fixture: Fixture) {
        advanceUntilIdle()
        assertIs<AppLockState.Locked>(fixture.viewModel.state.value)
        fixture.viewModel.authenticate()
        advanceUntilIdle()
        fixture.gateway.complete(BiometricAuthenticationResult.Succeeded)
        advanceUntilIdle()
        assertEquals(AppLockState.Unlocked, fixture.viewModel.state.value)
    }

    private class MutableElapsedClock(var now: Long = 0L) : ElapsedRealtimeClock {
        override fun nowMillis(): Long = now
    }

    private class FakeBiometricGateway(
        var capability: BiometricCapability,
    ) : BiometricAuthenticationGateway {
        private val callbacks = linkedMapOf<Long, (Long, BiometricAuthenticationResult) -> Unit>()
        var lastToken: Long = 0L
            private set
        var cancelCalls: Int = 0
            private set
        var capabilityGate: CompletableDeferred<Unit>? = null

        override suspend fun capability(): BiometricCapability {
            capabilityGate?.await()
            return capability
        }

        override fun authenticate(
            requestToken: Long,
            callback: (Long, BiometricAuthenticationResult) -> Unit,
        ) {
            lastToken = requestToken
            callbacks[requestToken] = callback
        }

        override fun cancelAuthentication() {
            cancelCalls++
        }

        fun complete(result: BiometricAuthenticationResult) = completeToken(lastToken, result)

        fun completeToken(token: Long, result: BiometricAuthenticationResult) {
            callbacks.getValue(token)(token, result)
        }
    }
}
