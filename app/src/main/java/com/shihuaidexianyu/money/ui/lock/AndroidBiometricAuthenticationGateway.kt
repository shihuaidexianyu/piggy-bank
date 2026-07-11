package com.shihuaidexianyu.money.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

class RebindableHost<T : Any> {
    private var reference = WeakReference<T>(null)

    fun attach(host: T) {
        reference = WeakReference(host)
    }

    fun detach(host: T) {
        if (reference.get() === host) reference.clear()
    }

    fun current(): T? = reference.get()
}

class AndroidBiometricAuthenticationGateway : BiometricAuthenticationGateway {
    private val host = RebindableHost<FragmentActivity>()
    private var activePrompt: BiometricPrompt? = null

    fun attachHost(activity: FragmentActivity) {
        host.attach(activity)
    }

    fun detachHost(activity: FragmentActivity) {
        if (host.current() === activity) cancelAuthentication()
        host.detach(activity)
    }

    override suspend fun capability(): BiometricCapability {
        val activity = host.current() ?: return BiometricCapability.TEMPORARILY_UNAVAILABLE
        return mapBiometricCapability(
            BiometricManager.from(activity).canAuthenticate(biometricAuthenticators()),
        )
    }

    override fun authenticate(
        requestToken: Long,
        callback: (Long, BiometricAuthenticationResult) -> Unit,
    ) {
        val activity = host.current()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            callback(
                requestToken,
                BiometricAuthenticationResult.Error("身份验证界面不可用"),
            )
            return
        }
        cancelAuthentication()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activePrompt = null
                    callback(requestToken, BiometricAuthenticationResult.Succeeded)
                }

                override fun onAuthenticationFailed() {
                    mapBiometricNonTerminalFailure()?.let { callback(requestToken, it) }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activePrompt = null
                    callback(requestToken, mapBiometricError(errorCode, errString.toString()))
                }
            },
        )
        activePrompt = prompt
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁小金库")
                .setSubtitle("验证生物识别以查看你的账本")
                .setAllowedAuthenticators(biometricAuthenticators())
                .setNegativeButtonText("取消")
                .build(),
        )
    }

    override fun cancelAuthentication() {
        activePrompt?.cancelAuthentication()
        activePrompt = null
    }
}

fun biometricAuthenticators(): Int = BiometricManager.Authenticators.BIOMETRIC_WEAK

fun mapBiometricNonTerminalFailure(): BiometricAuthenticationResult? = null

fun mapBiometricCapability(result: Int): BiometricCapability = when (result) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
    else -> BiometricCapability.TEMPORARILY_UNAVAILABLE
}

fun mapBiometricError(errorCode: Int, message: String): BiometricAuthenticationResult =
    when (errorCode) {
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        -> BiometricAuthenticationResult.Cancelled
        else -> BiometricAuthenticationResult.Error(message)
    }
