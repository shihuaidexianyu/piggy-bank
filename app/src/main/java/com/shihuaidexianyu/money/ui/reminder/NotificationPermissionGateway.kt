package com.shihuaidexianyu.money.ui.reminder

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.notification.AndroidMoneyNotificationPublisher
import kotlinx.coroutines.launch

data class NotificationPermissionGateway(
    val state: NotificationPermissionUiState,
    val requestPending: Boolean,
    val requestContextually: () -> Boolean,
    val openSettings: (NotificationSettingsTarget) -> Unit,
)

@Composable
fun rememberNotificationPermissionGateway(
    devicePreferencesRepository: DevicePreferencesRepository,
    notificationSyncRequester: NotificationSyncRequester,
): NotificationPermissionGateway {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val preferences by devicePreferencesRepository.observe().collectAsStateWithLifecycle(
        initialValue = DevicePreferences(),
    )
    var refreshToken by remember { mutableIntStateOf(0) }
    var requestPending by rememberSaveable { mutableStateOf(false) }
    var pendingSettingsTargetName by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            handleNotificationPermissionResult(
                granted = granted,
                devicePreferencesRepository = devicePreferencesRepository,
                notificationSyncRequester = notificationSyncRequester,
            )
            refreshToken++
            requestPending = false
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    refreshToken
    val state = resolveNotificationPermissionState(readNotificationPermissionFacts(context, preferences))
    LaunchedEffect(state) {
        val pendingTarget = pendingSettingsTargetName?.let(NotificationSettingsTarget::valueOf)
        if (shouldSyncAfterNotificationSettingsReturn(pendingTarget, state)) {
            pendingSettingsTargetName = null
            runCatching { notificationSyncRequester.request(com.shihuaidexianyu.money.domain.notification.NotificationSyncReason.PERMISSION_GRANTED) }
        } else if (state is NotificationPermissionUiState.SettingsRequired) {
            pendingSettingsTargetName = state.target.name
        }
    }
    return NotificationPermissionGateway(
        state = state,
        requestPending = requestPending,
        requestContextually = request@{
            if (!shouldLaunchRuntimeNotificationPermission(Build.VERSION.SDK_INT, state)) {
                return@request false
            } else {
                requestPending = true
                val launched = runCatching { launcher.launch(POST_NOTIFICATIONS_PERMISSION) }.isSuccess
                if (!launched) {
                    requestPending = false
                    scope.launch {
                        handleNotificationPermissionRequestOutcome(
                            NotificationPermissionRequestOutcome.LAUNCH_FAILED,
                            devicePreferencesRepository,
                            notificationSyncRequester,
                        )
                    }
                }
                return@request launched
            }
        },
        openSettings = { target ->
            pendingSettingsTargetName = target.name
            openNotificationSettings(context, target)
        },
    )
}

private fun readNotificationPermissionFacts(
    context: Context,
    preferences: DevicePreferences,
): NotificationPermissionFacts {
    val manager = context.getSystemService(NotificationManager::class.java)
    fun channelEnabled(channelId: String): Boolean = manager?.getNotificationChannel(channelId)
        ?.importance != NotificationManager.IMPORTANCE_NONE
    return NotificationPermissionFacts(
        apiLevel = Build.VERSION.SDK_INT,
        runtimePermissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            POST_NOTIFICATIONS_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED,
        permissionRequested = preferences.notificationPermissionRequested,
        shouldShowRationale = context.findActivity()
            ?.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS_PERMISSION) == true,
        appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        recurringChannelEnabled = channelEnabled(AndroidMoneyNotificationPublisher.RECURRING_CHANNEL_ID),
        balanceChannelEnabled = channelEnabled(AndroidMoneyNotificationPublisher.BALANCE_CHANNEL_ID),
    )
}

private fun openNotificationSettings(context: Context, target: NotificationSettingsTarget) {
    val intent = when (target) {
        NotificationSettingsTarget.APPLICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        NotificationSettingsTarget.RECURRING_CHANNEL -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidMoneyNotificationPublisher.RECURRING_CHANNEL_ID)
        NotificationSettingsTarget.BALANCE_CHANNEL -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidMoneyNotificationPublisher.BALANCE_CHANNEL_ID)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
