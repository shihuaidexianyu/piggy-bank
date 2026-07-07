package com.shihuaidexianyu.money

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.BiometricGatekeeper
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import com.shihuaidexianyu.money.util.SharedTextAmountExtractor
import kotlinx.coroutines.flow.first

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MoneyApplication).container
        val shortcutAction = intent?.getStringExtra("shortcut_action")
        val sharedAmount = extractSharedAmount(intent)
        setContent {
            val settings by container.settingsRepository.observeSettings().collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )

            // Detect when the real DataStore value has loaded. We can't use `collectAsStateWithLifecycle`'s
            // initial value as a sentinel (the user might genuinely have all-default settings), so we
            // use a separate `produceState` that reads the first emission synchronously.
            val settingsLoaded by produceState(initialValue = false) {
                // This runs in a coroutine; `first()` suspends until the DataStore emits.
                container.settingsRepository.observeSettings().first()
                value = true
            }

            MoneyTheme(
                themeMode = settings.themeMode,
                amountColorMode = settings.amountColorMode,
            ) {
                BiometricGatekeeper(
                    enabled = settings.biometricLock,
                    settingsLoaded = settingsLoaded,
                    onUnlocked = {},
                ) {
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
