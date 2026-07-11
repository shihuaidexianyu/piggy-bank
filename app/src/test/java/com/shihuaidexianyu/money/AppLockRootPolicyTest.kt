package com.shihuaidexianyu.money

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.ui.lock.AppLockState
import com.shihuaidexianyu.money.ui.lock.AppLockUnavailableReason
import com.shihuaidexianyu.money.ui.lock.AppRootSurface
import com.shihuaidexianyu.money.ui.lock.BiometricCapability
import com.shihuaidexianyu.money.ui.lock.mapBiometricCapability
import com.shihuaidexianyu.money.ui.lock.mapBiometricError
import com.shihuaidexianyu.money.ui.lock.BiometricAuthenticationResult
import com.shihuaidexianyu.money.ui.lock.resolveAppRootSurface
import com.shihuaidexianyu.money.ui.lock.biometricAuthenticators
import com.shihuaidexianyu.money.ui.lock.mapBiometricNonTerminalFailure
import com.shihuaidexianyu.money.ui.lock.RebindableHost
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertEquals
import org.junit.Test

class AppLockRootPolicyTest {
    @Test
    fun `ledger is composed only after both unlock and startup ready`() {
        val lockedStates = listOf(
            AppLockState.Loading,
            AppLockState.Locked,
            AppLockState.Authenticating,
            AppLockState.Unavailable(AppLockUnavailableReason.NO_HARDWARE),
        )
        lockedStates.forEach { lockState ->
            assertEquals(
                AppRootSurface.LOCK,
                resolveAppRootSurface(StartupMigrationState.Ready, lockState),
            )
        }
        assertEquals(
            AppRootSurface.STARTUP,
            resolveAppRootSurface(StartupMigrationState.Loading, AppLockState.Unlocked),
        )
        assertEquals(
            AppRootSurface.LEDGER,
            resolveAppRootSurface(StartupMigrationState.Ready, AppLockState.Unlocked),
        )
    }

    @Test
    fun `android biometric capability codes map without a bail open case`() {
        assertEquals(
            BiometricCapability.AVAILABLE,
            mapBiometricCapability(BiometricManager.BIOMETRIC_SUCCESS),
        )
        assertEquals(
            BiometricCapability.NO_HARDWARE,
            mapBiometricCapability(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE),
        )
        assertEquals(
            BiometricCapability.NOT_ENROLLED,
            mapBiometricCapability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
        assertEquals(
            BiometricCapability.TEMPORARILY_UNAVAILABLE,
            mapBiometricCapability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE),
        )
        assertEquals(BiometricCapability.TEMPORARILY_UNAVAILABLE, mapBiometricCapability(-999))
    }

    @Test
    fun `android cancellation codes remain cancelled and other errors remain locked errors`() {
        for (code in listOf(
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        )) {
            assertEquals(BiometricAuthenticationResult.Cancelled, mapBiometricError(code, "cancel"))
        }
        assertEquals(
            BiometricAuthenticationResult.Error("lockout"),
            mapBiometricError(BiometricPrompt.ERROR_LOCKOUT, "lockout"),
        )
    }

    @Test
    fun `biometric capability policy does not treat device credential as biometric`() {
        val authenticators = biometricAuthenticators()
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_WEAK, authenticators)
        assertEquals(0, authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        assertNull(mapBiometricNonTerminalFailure())
    }

    @Test
    fun `rebound host ignores stale activity detach and retains only newest host weakly`() {
        val store = RebindableHost<Any>()
        val oldHost = Any()
        val newHost = Any()
        store.attach(oldHost)
        store.attach(newHost)

        store.detach(oldHost)

        assertSame(newHost, store.current())
        store.detach(newHost)
        assertNull(store.current())
    }
}
