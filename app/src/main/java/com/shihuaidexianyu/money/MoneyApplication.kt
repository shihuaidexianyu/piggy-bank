package com.shihuaidexianyu.money

import android.app.Application
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider
import com.shihuaidexianyu.money.notification.AndroidMoneyNotificationPublisher
import com.shihuaidexianyu.money.notification.MoneyNotificationScheduler
import com.shihuaidexianyu.money.widget.BalanceOverviewWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import kotlinx.coroutines.flow.first

class MoneyApplication : Application(), MoneyAppContainerProvider {
    lateinit var container: MoneyAppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val moneyAppContainer: MoneyAppContainer get() = container

    override fun onCreate() {
        super.onCreate()
        container = MoneyAppContainer(this)
        appScope.launch {
            container.startupMigrationCoordinator.runMigration()
            container.startupMigrationCoordinator.state.first { it == StartupMigrationState.Ready }
            AndroidMoneyNotificationPublisher.ensureChannels(this@MoneyApplication)
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
            BalanceOverviewWidgetProvider.scheduleUpdate(this@MoneyApplication)
            container.seedDebugSampleDataIfNeeded()
        }
    }
}
