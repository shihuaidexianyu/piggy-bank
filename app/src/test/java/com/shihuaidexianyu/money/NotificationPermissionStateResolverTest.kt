package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionFacts
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.reminder.resolveNotificationPermissionState
import com.shihuaidexianyu.money.ui.reminder.handleNotificationPermissionResult
import com.shihuaidexianyu.money.ui.reminder.handleNotificationPermissionRequestOutcome
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionRequestOutcome
import com.shihuaidexianyu.money.ui.reminder.shouldLaunchRuntimeNotificationPermission
import com.shihuaidexianyu.money.ui.reminder.shouldSyncAfterNotificationSettingsReturn
import com.shihuaidexianyu.money.ui.reminder.shouldFinishPendingPermissionNavigation
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test

class NotificationPermissionStateResolverTest {
    @Test
    fun `api 33 first contextual request is distinguished from retry and permanent denial`() {
        val base = NotificationPermissionFacts(
            apiLevel = 33,
            runtimePermissionGranted = false,
            permissionRequested = false,
            shouldShowRationale = false,
            appNotificationsEnabled = true,
            recurringChannelEnabled = true,
            balanceChannelEnabled = true,
        )

        assertEquals(NotificationPermissionUiState.NotRequested, resolveNotificationPermissionState(base))
        assertEquals(
            NotificationPermissionUiState.Denied(canRequestAgain = true),
            resolveNotificationPermissionState(base.copy(permissionRequested = true, shouldShowRationale = true)),
        )
        assertEquals(
            NotificationPermissionUiState.Denied(canRequestAgain = false),
            resolveNotificationPermissionState(base.copy(permissionRequested = true)),
        )
        assertEquals(true, shouldLaunchRuntimeNotificationPermission(33, NotificationPermissionUiState.NotRequested))
        assertEquals(true, shouldLaunchRuntimeNotificationPermission(33, NotificationPermissionUiState.Denied(true)))
        assertEquals(false, shouldLaunchRuntimeNotificationPermission(33, NotificationPermissionUiState.Denied(false)))
    }

    @Test
    fun `api 31 and 32 never expose runtime permission launcher`() {
        val facts = NotificationPermissionFacts(
            apiLevel = 31,
            runtimePermissionGranted = false,
            permissionRequested = false,
            shouldShowRationale = false,
            appNotificationsEnabled = true,
            recurringChannelEnabled = true,
            balanceChannelEnabled = true,
        )

        assertEquals(NotificationPermissionUiState.Granted, resolveNotificationPermissionState(facts))
        assertEquals(NotificationPermissionUiState.Granted, resolveNotificationPermissionState(facts.copy(apiLevel = 32)))
        assertEquals(false, shouldLaunchRuntimeNotificationPermission(31, NotificationPermissionUiState.NotRequested))
        assertEquals(false, shouldLaunchRuntimeNotificationPermission(32, NotificationPermissionUiState.NotRequested))
    }

    @Test
    fun `application and channel shutdowns route to exact settings targets`() {
        val allowed = NotificationPermissionFacts(
            apiLevel = 36,
            runtimePermissionGranted = true,
            permissionRequested = true,
            shouldShowRationale = false,
            appNotificationsEnabled = true,
            recurringChannelEnabled = true,
            balanceChannelEnabled = true,
        )

        assertEquals(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.APPLICATION),
            resolveNotificationPermissionState(allowed.copy(appNotificationsEnabled = false)),
        )
        assertEquals(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.RECURRING_CHANNEL),
            resolveNotificationPermissionState(allowed.copy(recurringChannelEnabled = false)),
        )
        assertEquals(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.BALANCE_CHANNEL),
            resolveNotificationPermissionState(allowed.copy(balanceChannelEnabled = false)),
        )
    }

    @Test
    fun `returning from any app or channel settings target to granted requests sync`() {
        NotificationSettingsTarget.entries.forEach { target ->
            assertEquals(
                true,
                shouldSyncAfterNotificationSettingsReturn(
                    previousTarget = target,
                    currentState = NotificationPermissionUiState.Granted,
                ),
            )
        }
        assertEquals(
            false,
            shouldSyncAfterNotificationSettingsReturn(
                previousTarget = NotificationSettingsTarget.BALANCE_CHANNEL,
                currentState = NotificationPermissionUiState.SettingsRequired(
                    NotificationSettingsTarget.BALANCE_CHANNEL,
                ),
            ),
        )
        assertEquals(
            true,
            shouldSyncAfterNotificationSettingsReturn(
                previousTarget = NotificationSettingsTarget.BALANCE_CHANNEL,
                currentState = NotificationPermissionUiState.SettingsRequired(
                    NotificationSettingsTarget.RECURRING_CHANNEL,
                ),
            ),
        )
        assertEquals(
            true,
            shouldSyncAfterNotificationSettingsReturn(
                previousTarget = NotificationSettingsTarget.RECURRING_CHANNEL,
                currentState = NotificationPermissionUiState.SettingsRequired(
                    NotificationSettingsTarget.BALANCE_CHANNEL,
                ),
            ),
        )
        assertEquals(
            true,
            shouldSyncAfterNotificationSettingsReturn(
                previousTarget = NotificationSettingsTarget.APPLICATION,
                currentState = NotificationPermissionUiState.SettingsRequired(
                    NotificationSettingsTarget.RECURRING_CHANNEL,
                ),
            ),
        )
        assertEquals(
            false,
            shouldSyncAfterNotificationSettingsReturn(
                previousTarget = NotificationSettingsTarget.APPLICATION,
                currentState = NotificationPermissionUiState.Denied(canRequestAgain = false),
            ),
        )
    }

    @Test
    fun `restored form navigation finishes only after restored permission request completes`() {
        assertEquals(false, shouldFinishPendingPermissionNavigation(true, true))
        assertEquals(true, shouldFinishPendingPermissionNavigation(true, false))
        assertEquals(false, shouldFinishPendingPermissionNavigation(false, false))
    }

    @Test
    fun `only successful runtime result requests notification sync while both persist requested`() = runBlocking {
        val deniedPreferences = InMemoryDevicePreferencesRepository()
        val deniedRequester = RecordingRequester()
        handleNotificationPermissionResult(false, deniedPreferences, deniedRequester)
        assertEquals(true, deniedPreferences.query().notificationPermissionRequested)
        assertEquals(emptyList(), deniedRequester.reasons)

        val grantedPreferences = InMemoryDevicePreferencesRepository()
        val grantedRequester = RecordingRequester()
        handleNotificationPermissionResult(true, grantedPreferences, grantedRequester)
        assertEquals(true, grantedPreferences.query().notificationPermissionRequested)
        assertEquals(listOf(NotificationSyncReason.PERMISSION_GRANTED), grantedRequester.reasons)
    }

    @Test
    fun `launcher failure keeps permission not requested and never syncs`() = runBlocking {
        val preferences = InMemoryDevicePreferencesRepository()
        val requester = RecordingRequester()

        handleNotificationPermissionRequestOutcome(
            NotificationPermissionRequestOutcome.LAUNCH_FAILED,
            preferences,
            requester,
        )

        assertEquals(false, preferences.query().notificationPermissionRequested)
        assertEquals(emptyList(), requester.reasons)
    }

    @Test
    fun `grant sync is independent from preference persistence failure`() = runBlocking {
        val delegate = InMemoryDevicePreferencesRepository()
        val failingPreferences = object : DevicePreferencesRepository by delegate {
            override suspend fun updateNotificationPermissionRequested(requested: Boolean) {
                error("datastore unavailable")
            }
        }
        val requester = RecordingRequester()

        handleNotificationPermissionRequestOutcome(
            NotificationPermissionRequestOutcome.GRANTED,
            failingPreferences,
            requester,
        )

        assertEquals(false, delegate.query().notificationPermissionRequested)
        assertEquals(listOf(NotificationSyncReason.PERMISSION_GRANTED), requester.reasons)
    }

    private class RecordingRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()
        override fun request(reason: NotificationSyncReason) {
            reasons += reason
        }
    }
}
