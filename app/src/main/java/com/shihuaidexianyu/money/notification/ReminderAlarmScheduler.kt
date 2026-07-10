package com.shihuaidexianyu.money.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.MoneyApplication
import com.shihuaidexianyu.money.domain.model.RecurringReminder

/**
 * Schedules exact-timestamp alarms for individual recurring reminders using [AlarmManager].
 * Unlike the 15-minute periodic [RecurringReminderWorker], alarms fire at the precise [RecurringReminder.nextDueAt]
 * millisecond. When an alarm fires, [ReminderAlarmReceiver] posts a notification and (eventually)
 * the next alarm should be re-scheduled by the app when it processes the reminder.
 *
 * On Android 12+ (API 31+), [AlarmManager.canScheduleExactAlarms] must be true. If not, we fall
 * back silently to the periodic WorkManager check — the user just gets a ~15 min delay instead
 * of exact-time. The app should prompt the user to grant `SCHEDULE_EXACT_ALARM` in settings.
 */
object ReminderAlarmScheduler {

    fun scheduleReminderAlarm(context: Context, reminder: RecurringReminder) {
        if (!reminder.isEnabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Can't schedule exact alarms — fall back to WorkManager periodic check.
            return
        }
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.shihuaidexianyu.money.REMINDER_DUE"
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_name", reminder.name)
            putExtra("reminder_amount", reminder.amount)
            putExtra("reminder_account_id", reminder.accountId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setExactAndAllowWhileIdle fires even in Doze mode (with a small per-app quota).
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.nextDueAt,
            pendingIntent,
        )
    }

    fun cancelReminderAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.shihuaidexianyu.money.REMINDER_DUE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}

/**
 * Receives exact-alarm broadcasts for due reminders and posts a notification immediately.
 * This is the precise-fire counterpart to the coarse [RecurringReminderWorker].
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.shihuaidexianyu.money.REMINDER_DUE") return
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        val name = intent.getStringExtra("reminder_name") ?: "提醒"
        val amount = intent.getLongExtra("reminder_amount", 0L)
        val accountId = intent.getLongExtra("reminder_account_id", -1L)
        if (reminderId < 0) return

        val container = (context.applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            ?: return
        if (container.startupMigrationCoordinator.withReadyLedgerAccess { true } == null) return
        val (accountName, settings) = kotlinx.coroutines.runBlocking {
            val accountName = if (accountId > 0) {
                runCatching { container.accountRepository.getAccountById(accountId)?.name }.getOrNull()
            } else {
                null
            }
            accountName to container.portableSettingsRepository.query()
        }

        val amountText = com.shihuaidexianyu.money.util.AmountFormatter.format(
            amount,
            settings,
        )
        ReminderNotifications.postReminder(
            context = context,
            reminderId = reminderId,
            name = name,
            amountText = amountText,
            accountName = accountName,
        )
    }
}
