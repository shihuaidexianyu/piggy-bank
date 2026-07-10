package com.shihuaidexianyu.money.ui.common

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Full-screen biometric lock. Shows [content] only after the user authenticates.
 *
 * Must be hosted in a [FragmentActivity] because [BiometricPrompt] requires a
 * `FragmentActivity`/`Fragment` host.
 *
 * State machine:
 * - START: `locked = true`, `settingsReady = false`. Show a blank loading screen (no content, no
 *   lock UI yet) — we don't know if biometric is enabled until DataStore loads.
 * - When [enabled] flips to `false`: `locked = false` → show content.
 * - When [enabled] flips to `true` AND biometrics are available: show BiometricPrompt. On success,
 *   `locked = false` → show content.
 * - If biometrics are unavailable while [enabled] is `true`: `locked = false` (bail open to avoid
 *   locking the user out forever).
 *
 * The critical fix: we track `settingsReady` so the initial `enabled = false` (from the
 * `DevicePreferences()` default) does NOT prematurely unlock. Only when the real DataStore value
 * arrives (signaled by [enabled] changing from its initial `false`) do we act.
 *
 * However, since we can't distinguish "DataStore loaded and biometricLock is false" from "DataStore
 * not yet loaded, default is false", we use a different approach: the caller passes the *raw*
 * settings object so we can detect when it has been loaded by checking if any non-default field
 * is present. Simpler: the caller sets `enabled` only after settings are loaded.
 *
 * Actually the simplest correct fix: don't use `initialValue = DevicePreferences()` — the caller should
 * track loading state. But to keep changes minimal, we use a `settingsLoaded` flag that the caller
 * must set to `true` once the DataStore has emitted at least once.
 */
@Composable
fun BiometricGatekeeper(
    enabled: Boolean,
    settingsLoaded: Boolean,
    onUnlocked: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var locked by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var promptVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(settingsLoaded, enabled) {
        // Don't do anything until settings have been loaded from DataStore.
        if (!settingsLoaded) return@LaunchedEffect

        if (!enabled) {
            locked = false
            onUnlocked()
            return@LaunchedEffect
        }
        if (activity == null) {
            locked = false
            onUnlocked()
            return@LaunchedEffect
        }
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric enrolled or hardware missing — bail open.
            locked = false
            onUnlocked()
            return@LaunchedEffect
        }
        // Biometric is enabled and available — show the prompt.
        promptVisible = true
    }

    if (!locked) {
        content()
        return
    }

    // Don't show the lock UI until settings are loaded (avoids flash of lock screen when
    // biometric is actually disabled).
    if (!settingsLoaded) return

    // Show the biometric prompt when requested.
    if (promptVisible && activity != null) {
        BiometricPromptContainer(
            activity = activity,
            onSuccess = {
                locked = false
                errorMessage = null
                promptVisible = false
                onUnlocked()
            },
            onError = { message ->
                errorMessage = message
                promptVisible = false
            },
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "小金库已锁定",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请验证身份以继续使用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = {
                errorMessage = null
                promptVisible = true
            }) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun BiometricPromptContainer(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁小金库")
            .setSubtitle("验证身份以查看你的账本")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }
}
