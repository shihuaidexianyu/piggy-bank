package com.shihuaidexianyu.money

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.failClosedDevicePreferences
import com.shihuaidexianyu.money.ui.lock.AppLockScreen
import com.shihuaidexianyu.money.ui.lock.AppLockViewModel
import com.shihuaidexianyu.money.ui.lock.AppRootSurface
import com.shihuaidexianyu.money.ui.lock.ElapsedRealtimeClock
import com.shihuaidexianyu.money.ui.lock.resolveAppRootSurface
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.domain.notification.NotificationCapability
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.ui.launch.AndroidAppLaunchIntentParser
import com.shihuaidexianyu.money.ui.launch.AppLaunchQueueViewModel
import java.util.UUID

class MainActivity : FragmentActivity() {
    private var notificationsWereAllowed: Boolean? = null
    private val appLaunchQueueViewModel: AppLaunchQueueViewModel by viewModels()
    private val appLockViewModel: AppLockViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as MoneyApplication).container
                AppLockViewModel(
                    preferencesRepository = container.devicePreferencesRepository,
                    biometricGateway = (application as MoneyApplication).biometricAuthenticationGateway,
                    elapsedRealtimeClock = ElapsedRealtimeClock(SystemClock::elapsedRealtime),
                    onPrivacyDefaultsEnabled = {
                        (application as MoneyApplication).forceRefreshNotificationPrivacy()
                    },
                    onPrivacyDefaultsEnabling = {
                        (application as MoneyApplication).prepareExternalPrivacyEnable()
                    },
                    onPrivacyDefaultsEnableFailed = {
                        (application as MoneyApplication).recoverExternalPrivacyEnableFailure()
                    },
                )
            }
        }
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) appLockViewModel.onScreenOff()
        }
    }
    private var screenOffReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val moneyApplication = application as MoneyApplication
        moneyApplication.biometricAuthenticationGateway.attachHost(this)
        val container = moneyApplication.container
        publishAppLaunch(intent)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
        // Fail closed until the persisted task-snapshot preference has been loaded.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val startupState by container.startupMigrationCoordinator.state.collectAsStateWithLifecycle()
            val lockState by appLockViewModel.state.collectAsStateWithLifecycle()
            val portableSettings by produceState(initialValue = PortableSettings()) {
                container.startupMigrationCoordinator.state.first { it == StartupMigrationState.Ready }
                container.portableSettingsRepository.observe().collect { value = it }
            }
            val devicePreferencesFlow = remember(container) {
                container.devicePreferencesRepository.observe().catch {
                    emit(failClosedDevicePreferences())
                }
            }
            val loadedDevicePreferences by produceState<DevicePreferences?>(initialValue = null) {
                devicePreferencesFlow.collect { value = it }
            }
            val devicePreferences = loadedDevicePreferences ?: DevicePreferences()
            val pendingLaunchRequests by appLaunchQueueViewModel.pending.collectAsStateWithLifecycle()
            LaunchedEffect(devicePreferences.relockDelay) {
                appLockViewModel.onRelockDelayChanged(devicePreferences.relockDelay)
            }
            SideEffect {
                if (loadedDevicePreferences?.hideRecentTasks == false) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MoneyTheme(
                themeMode = devicePreferences.themeMode,
                amountColorMode = portableSettings.amountColorMode,
            ) {
                val effectiveLockState = if (loadedDevicePreferences == null) {
                    com.shihuaidexianyu.money.ui.lock.AppLockState.Loading
                } else {
                    lockState
                }
                LaunchedEffect(effectiveLockState) {
                    if (effectiveLockState == com.shihuaidexianyu.money.ui.lock.AppLockState.Locked) {
                        appLockViewModel.authenticateAutomaticallyOnce()
                    }
                }
                when (resolveAppRootSurface(startupState, effectiveLockState)) {
                    AppRootSurface.LOCK -> AppLockScreen(
                        state = effectiveLockState,
                        onAuthenticate = appLockViewModel::authenticate,
                        onOpenSecuritySettings = {
                            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        },
                    )
                    AppRootSurface.STARTUP -> StartupMigrationSurface(
                        container = container,
                        state = startupState,
                    )
                    AppRootSurface.LEDGER -> MoneyApp(
                        container = container,
                        appLaunchRequest = pendingLaunchRequests.firstOrNull(),
                        onAppLaunchConsumed = appLaunchQueueViewModel::acknowledge,
                        onBiometricLockChange = { enabled ->
                            if (enabled) {
                                appLockViewModel.enableBiometricLock()
                            } else {
                                appLockViewModel.disableBiometricLock()
                            }
                        },
                        amountPrivacy = AmountPrivacy.from(devicePreferences),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishAppLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        appLockViewModel.onForegrounded()
        appLockViewModel.authenticateAutomaticallyForForeground()
    }

    override fun onStop() {
        appLockViewModel.onBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        (application as? MoneyApplication)?.biometricAuthenticationGateway?.detachHost(this)
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
        super.onDestroy()
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

    private fun publishAppLaunch(sourceIntent: Intent?) {
        val request = AndroidAppLaunchIntentParser.parse(
            intent = sourceIntent,
            token = UUID.randomUUID().toString(),
        )
        request?.let(appLaunchQueueViewModel::offer)
        // SavedState owns the durable queue. Keep Activity's source Intent free of share text,
        // shortcut extras and notification identity so recreation cannot enqueue it twice.
        setIntent(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            },
        )
    }
}
