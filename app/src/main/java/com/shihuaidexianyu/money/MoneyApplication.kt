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

class MoneyApplication : Application(), MoneyAppContainerProvider {
    lateinit var container: MoneyAppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val moneyAppContainer: MoneyAppContainer get() = container

    override fun onCreate() {
        super.onCreate()
        container = MoneyAppContainer(this)
        // Schedule the recurring-reminder worker so the user gets OS notifications even when the
        // app is not in the foreground. Uses KEEP policy so re-launching the app doesn't duplicate.
        ReminderNotificationScheduler.schedule(this)
        // Schedule periodic widget refresh (every 30 min, the system minimum for updatePeriodMillis).
        BalanceOverviewWidgetProvider.scheduleUpdate(this)
        // Register exact-alarm reminders for all existing recurring reminders. The periodic
        // WorkManager check is the coarse fallback; alarms fire at the precise due time.
        appScope.launch {
            runCatching {
                val reminders = container.recurringReminderRepository.queryAll()
                reminders.forEach { reminder ->
                    if (reminder.isEnabled) {
                        ReminderAlarmScheduler.scheduleReminderAlarm(this@MoneyApplication, reminder)
                    }
                }
            }
        }
        appScope.launch {
            container.seedDebugSampleDataIfNeeded()
        }
    }
}
