package com.shihuaidexianyu.money

import android.app.Application
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider
import com.shihuaidexianyu.money.notification.ReminderAlarmScheduler
import com.shihuaidexianyu.money.notification.ReminderNotificationScheduler
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
            ReminderNotificationScheduler.schedule(this@MoneyApplication)
            BalanceOverviewWidgetProvider.scheduleUpdate(this@MoneyApplication)
            runCatching {
                val reminders = container.recurringReminderRepository.queryAll()
                reminders.forEach { reminder ->
                    if (reminder.isEnabled) {
                        ReminderAlarmScheduler.scheduleReminderAlarm(this@MoneyApplication, reminder)
                    }
                }
            }
            container.seedDebugSampleDataIfNeeded()
        }
    }
}
