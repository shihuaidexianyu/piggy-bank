package com.shihuaidexianyu.money.ui.common

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
 * Full-screen biometric lock. Shows [content] only after the user authenticates (or if
 * [BiometricManager] reports that biometrics are not available, in which case the lock is
 * bypassed — the user should disable biometric lock in settings to avoid being stuck).
 *
 * Must be hosted in a [FragmentActivity] because [BiometricPrompt] requires a
 * `FragmentActivity`/`Fragment` host.
 *
 * State logic: `unlocked` starts `false` (locked). A [LaunchedEffect] watches [enabled] —
 * when it becomes `false` (or biometrics are unavailable), `unlocked` flips to `true`.
 * When [enabled] is `true` and biometrics are available, the [BiometricPrompt] is shown;
 * on success, `unlocked` flips to `true`.
 */
@Composable
fun BiometricGatekeeper(
    enabled: Boolean,
    onUnlocked: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Always start locked. The LaunchedEffect below will unlock immediately if biometric is
    // disabled or unavailable. This fixes the bug where `initialValue = AppSettings()` had
    // `biometricLock = false`, causing `unlocked` to initialize as `true` and never reset
    // when the real DataStore value (`biometricLock = true`) arrived a moment later.
    var unlocked by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var promptVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(enabled) {
        if (!enabled) {
            unlocked = true
            onUnlocked()
            return@LaunchedEffect
        }
        if (activity == null) {
            // Can't show biometric prompt without FragmentActivity — bail open.
            unlocked = true
            onUnlocked()
            return@LaunchedEffect
        }
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric enrolled or hardware missing — bail open to avoid locking the user out.
            unlocked = true
            onUnlocked()
            return@LaunchedEffect
        }
        // Biometric is enabled and available — show the prompt.
        promptVisible = true
    }

    if (unlocked) {
        content()
        return
    }

    // Show the biometric prompt when requested. Uses a key so that tapping "重试" (which
    // toggles promptVisible) re-triggers the LaunchedEffect and re-shows the system prompt.
    if (promptVisible && activity != null) {
        BiometricPromptContainer(
            activity = activity,
            onSuccess = {
                unlocked = true
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
                // Re-trigger the biometric prompt.
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
