package com.shihuaidexianyu.money.ui.reminder

import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository

data class NotificationPermissionFacts(
    val apiLevel: Int,
    val runtimePermissionGranted: Boolean,
    val permissionRequested: Boolean,
    val shouldShowRationale: Boolean,
    val appNotificationsEnabled: Boolean,
    val recurringChannelEnabled: Boolean,
    val balanceChannelEnabled: Boolean,
)

internal data class NotificationSettingsSyncObservation(
    val state: NotificationPermissionUiState,
    val recurringChannelEnabled: Boolean,
    val balanceChannelEnabled: Boolean,
)

internal fun notificationSettingsSyncObservation(
    facts: NotificationPermissionFacts,
): NotificationSettingsSyncObservation = NotificationSettingsSyncObservation(
    state = resolveNotificationPermissionState(facts),
    recurringChannelEnabled = facts.recurringChannelEnabled,
    balanceChannelEnabled = facts.balanceChannelEnabled,
)

enum class NotificationSettingsTarget {
    APPLICATION,
    RECURRING_CHANNEL,
    BALANCE_CHANNEL,
}

sealed interface NotificationPermissionUiState {
    data object Granted : NotificationPermissionUiState
    data object NotRequested : NotificationPermissionUiState
    data class Denied(val canRequestAgain: Boolean) : NotificationPermissionUiState
    data class SettingsRequired(val target: NotificationSettingsTarget) : NotificationPermissionUiState
}

fun resolveNotificationPermissionState(
    facts: NotificationPermissionFacts,
): NotificationPermissionUiState {
    if (facts.apiLevel >= 33 && !facts.runtimePermissionGranted) {
        return if (!facts.permissionRequested) {
            NotificationPermissionUiState.NotRequested
        } else {
            NotificationPermissionUiState.Denied(facts.shouldShowRationale)
        }
    }
    if (!facts.appNotificationsEnabled) {
        return NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.APPLICATION)
    }
    if (!facts.recurringChannelEnabled) {
        return NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.RECURRING_CHANNEL)
    }
    if (!facts.balanceChannelEnabled) {
        return NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.BALANCE_CHANNEL)
    }
    return NotificationPermissionUiState.Granted
}

fun shouldLaunchRuntimeNotificationPermission(
    apiLevel: Int,
    state: NotificationPermissionUiState,
): Boolean = apiLevel >= 33 && (
    state == NotificationPermissionUiState.NotRequested ||
        state == NotificationPermissionUiState.Denied(canRequestAgain = true)
    )

fun shouldSyncAfterNotificationSettingsReturn(
    previousTarget: NotificationSettingsTarget?,
    currentState: NotificationPermissionUiState,
): Boolean {
    if (previousTarget == null) return false
    return when (currentState) {
        NotificationPermissionUiState.Granted -> true
        is NotificationPermissionUiState.SettingsRequired -> currentState.target != previousTarget
        NotificationPermissionUiState.NotRequested,
        is NotificationPermissionUiState.Denied,
        -> false
    }
}

fun shouldFinishPendingPermissionNavigation(
    navigationPending: Boolean,
    permissionRequestPending: Boolean,
): Boolean = navigationPending && !permissionRequestPending

suspend fun handleNotificationPermissionResult(
    granted: Boolean,
    devicePreferencesRepository: DevicePreferencesRepository,
    notificationSyncRequester: NotificationSyncRequester,
) {
    handleNotificationPermissionRequestOutcome(
        if (granted) NotificationPermissionRequestOutcome.GRANTED else NotificationPermissionRequestOutcome.DENIED,
        devicePreferencesRepository,
        notificationSyncRequester,
    )
}

enum class NotificationPermissionRequestOutcome {
    GRANTED,
    DENIED,
    LAUNCH_FAILED,
}

suspend fun handleNotificationPermissionRequestOutcome(
    outcome: NotificationPermissionRequestOutcome,
    devicePreferencesRepository: DevicePreferencesRepository,
    notificationSyncRequester: NotificationSyncRequester,
) {
    if (outcome == NotificationPermissionRequestOutcome.LAUNCH_FAILED) return
    runCatching {
        devicePreferencesRepository.updateNotificationPermissionRequested(true)
    }
    if (outcome == NotificationPermissionRequestOutcome.GRANTED) {
        runCatching { notificationSyncRequester.request(NotificationSyncReason.PERMISSION_GRANTED) }
    }
}
