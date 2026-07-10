package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.domain.model.PortableSettings

/**
 * Periodic [CoroutineWorker] that checks for due recurring reminders and posts a notification
 * for each. The in-app reminder list (observed via [ObserveDueRemindersUseCase]) still works
 * independently — this worker adds the OS-notification layer so the user is alerted even when
 * the app is not in the foreground.
 *
 * Scheduled by [ReminderNotificationScheduler] every ~15 minutes (the minimum WorkManager
 * periodic interval). Runs on a background thread; opens a short-lived DB connection via
 * [MoneyAppContainer] obtained from the [android.app.Application] context.
 */
class RecurringReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val container = (appContext.applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            ?: return Result.success()
        if (container.startupMigrationCoordinator.withReadyLedgerAccess { true } == null) {
            return Result.retry()
        }
        val reminderRepo = container.recurringReminderRepository
        val accountRepo = container.accountRepository

        val dueReminders = runCatching { reminderRepo.queryDue() }.getOrElse { return Result.retry() }
        if (dueReminders.isEmpty()) return Result.success()

        ReminderNotifications.ensureChannel(appContext)
        val settings = container.portableSettingsRepository.query()
        for (reminder in dueReminders) {
            if (!reminder.isEnabled) continue
            val account = runCatching { accountRepo.getAccountById(reminder.accountId) }.getOrNull()
            val amountText = AmountFormatter.format(reminder.amount, settings)
            ReminderNotifications.postReminder(
                context = appContext,
                reminderId = reminder.id,
                name = reminder.name,
                amountText = amountText,
                accountName = account?.name,
            )
        }
        return Result.success()
    }
}

/** Implemented by [com.shihuaidexianyu.money.MoneyApplication] so the worker can reach the DI container. */
interface MoneyAppContainerProvider {
    val moneyAppContainer: MoneyAppContainer
}
