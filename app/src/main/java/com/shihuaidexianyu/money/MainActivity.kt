package com.shihuaidexianyu.money

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.BiometricGatekeeper
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import com.shihuaidexianyu.money.util.SharedTextAmountExtractor

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate so the system shows it during cold start.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MoneyApplication).container
        val shortcutAction = intent?.getStringExtra("shortcut_action")
        val sharedAmount = extractSharedAmount(intent)
        setContent {
            val settings = container.settingsRepository.observeSettings().collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            MoneyTheme(
                themeMode = settings.value.themeMode,
                amountColorMode = settings.value.amountColorMode,
                dynamicColor = settings.value.dynamicColor,
            ) {
                BiometricGatekeeper(enabled = settings.value.biometricLock, onUnlocked = {}) {
                    MoneyApp(
                        container = container,
                        shortcutAction = shortcutAction,
                        sharedAmount = sharedAmount,
                    )
                }
            }
        }
    }

    private fun extractSharedAmount(intent: Intent?): Long? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return SharedTextAmountExtractor.extractAmountMillis(text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
