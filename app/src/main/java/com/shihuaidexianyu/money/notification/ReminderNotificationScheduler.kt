package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the two periodic workers that power background notifications:
 *
 * 1. [RecurringReminderWorker] — checks for due recurring reminders (subscriptions, manual bills).
 * 2. [BalanceCheckWorker] — checks all active accounts for stale balance-update status.
 *
 * Both use [ExistingPeriodicWorkPolicy.KEEP] so repeated calls (e.g. on every app start) don't
 * create duplicate work — only the first schedule survives.
 *
 * The 15-minute interval is the minimum WorkManager allows. For due-reminder and stale-balance
 * detection this is a reasonable trade-off between battery and timeliness — the user is alerted
 * within 15 minutes of the due/stale time rather than exactly on the minute.
 */
object ReminderNotificationScheduler {
    const val RECURRING_REMINDER_WORK_NAME = "recurring-reminder-check"
    const val BALANCE_CHECK_WORK_NAME = "balance-check"

    fun schedule(context: Context) {
        val recurringRequest = PeriodicWorkRequestBuilder<RecurringReminderWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECURRING_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            recurringRequest,
        )

        val balanceCheckRequest = PeriodicWorkRequestBuilder<BalanceCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BALANCE_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            balanceCheckRequest,
        )
    }
}
