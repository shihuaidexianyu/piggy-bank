package com.shihuaidexianyu.money

import android.app.Application
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider
import com.shihuaidexianyu.money.notification.AndroidMoneyNotificationPublisher
import com.shihuaidexianyu.money.notification.MoneyNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import kotlinx.coroutines.flow.first
import com.shihuaidexianyu.money.ui.lock.AndroidBiometricAuthenticationGateway

class MoneyApplication : Application(), MoneyAppContainerProvider {
    val biometricAuthenticationGateway = AndroidBiometricAuthenticationGateway()
    lateinit var container: MoneyAppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val moneyAppContainer: MoneyAppContainer get() = container

    override fun onCreate() {
        super.onCreate()
        // Channel creation is ledger-independent and must finish before any Activity can render
        // notification status. This also removes the first-launch race with the async migration.
        AndroidMoneyNotificationPublisher.ensureChannels(this)
        container = MoneyAppContainer(this)
        appScope.launch {
            container.startupMigrationCoordinator.runMigration()
            container.startupMigrationCoordinator.state.first { it == StartupMigrationState.Ready }
            val privacy = container.devicePreferencesRepository.query()
            if (privacy.hideNotificationAmounts) {
                container.syncMoneyNotificationsUseCase.forceRefreshPrivacy()
            }
            val legacyReminderIds = runCatching {
                container.recurringReminderRepository.queryAll().map { it.id }
            }.getOrDefault(emptyList())
            val legacyAccountIds = runCatching {
                container.accountRepository.queryAllAccounts().map { it.id }
            }.getOrDefault(emptyList())
            MoneyNotificationScheduler.scheduleAfterReady(
                context = this@MoneyApplication,
                legacyReminderIds = legacyReminderIds,
                legacyAccountIds = legacyAccountIds,
                requester = container.notificationSyncRequester,
            )
            container.seedDebugSampleDataIfNeeded()
        }
    }

    fun forceRefreshNotificationPrivacy() {
        appScope.launch {
            if (container.startupMigrationCoordinator.isReady) {
                container.syncMoneyNotificationsUseCase.forceRefreshPrivacy()
            }
        }
    }

    fun prepareExternalPrivacyEnable() {
        container.syncMoneyNotificationsUseCase.preparePrivacyRefresh()
    }

    fun recoverExternalPrivacyEnableFailure() {
        appScope.launch {
            container.syncMoneyNotificationsUseCase.forceRefreshPrivacy()
        }
    }
}
