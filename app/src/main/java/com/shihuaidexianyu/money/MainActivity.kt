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
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.BiometricGatekeeper
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import com.shihuaidexianyu.money.util.SharedTextAmountExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.domain.notification.NotificationCapability
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchRequest
import com.shihuaidexianyu.money.notification.NotificationLaunchIntentConsumer
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    private var notificationsWereAllowed: Boolean? = null
    private val notificationLaunchRequest = MutableStateFlow<NotificationLaunchRequest?>(null)
    private var nextLaunchToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MoneyApplication).container
        val shortcutAction = intent?.getStringExtra("shortcut_action")
        val sharedAmount = extractSharedAmount(intent)
        publishNotificationLaunch(intent)
        setContent {
            val portableSettings by produceState(initialValue = PortableSettings()) {
                container.startupMigrationCoordinator.state.first { it == StartupMigrationState.Ready }
                container.portableSettingsRepository.observe().collect { value = it }
            }
            val devicePreferencesFlow = remember(container) {
                container.devicePreferencesRepository.observe().catch { emit(DevicePreferences()) }
            }
            val devicePreferences by devicePreferencesFlow.collectAsStateWithLifecycle(
                initialValue = DevicePreferences(),
            )
            val notificationRequest by notificationLaunchRequest.collectAsStateWithLifecycle()

            // Detect when the real DataStore value has loaded. We can't use `collectAsStateWithLifecycle`'s
            // initial value as a sentinel (the user might genuinely have all-default settings), so we
            // use a separate `produceState` that reads the first emission synchronously.
            val settingsLoaded by produceState(initialValue = false) {
                // A corrupt preference file is surfaced by the startup coordinator. Marking this
                // read as finished lets the recoverable error page render without exposing ledger UI.
                try {
                    devicePreferencesFlow.first()
                } finally {
                    value = true
                }
            }

            MoneyTheme(
                themeMode = devicePreferences.themeMode,
                amountColorMode = portableSettings.amountColorMode,
            ) {
                BiometricGatekeeper(
                    enabled = devicePreferences.biometricLock,
                    settingsLoaded = settingsLoaded,
                    onUnlocked = {},
                ) {
                    MoneyApp(
                        container = container,
                        shortcutAction = shortcutAction,
                        sharedAmount = sharedAmount,
                        notificationLaunchRequest = notificationRequest,
                        onNotificationLaunchConsumed = { token ->
                            notificationLaunchRequest.compareAndSet(
                                notificationLaunchRequest.value?.takeIf { it.token == token },
                                null,
                            )
                        },
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
        publishNotificationLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        val app = application as? MoneyApplication ?: return
        val allowed = app.container.moneyNotificationPublisher.capability(
            MoneyNotificationKey.Recurring(0L),
        ) == NotificationCapability.Allowed
        if (notificationsWereAllowed == false && allowed) {
            app.container.notificationSyncRequester.request(NotificationSyncReason.PERMISSION_GRANTED)
        }
        notificationsWereAllowed = allowed
    }

    private fun publishNotificationLaunch(intent: Intent?) {
        val token = nextLaunchToken + 1
        val request = NotificationLaunchIntentConsumer.consume(intent, token) ?: return
        nextLaunchToken = token
        notificationLaunchRequest.value = request
    }
}
