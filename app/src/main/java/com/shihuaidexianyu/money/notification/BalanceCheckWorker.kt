package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.AccountStatusCalculator
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.ZoneId

/**
 * Periodic [CoroutineWorker] that checks all active accounts for stale balance-update status.
 * For each account whose last balance update is past its configured reminder time, posts a
 * "余额待核对" notification so the user knows to reconcile even when the app is not open.
 *
 * Scheduled by [ReminderNotificationScheduler] alongside [RecurringReminderWorker] every ~15 min.
 * Runs independently of the in-app stale-mark display — the notification is additive.
 */
class BalanceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val container = (appContext.applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            ?: return Result.success()
        val accountRepo = container.accountRepository
        val reminderSettingsRepo = container.accountReminderSettingsRepository

        val accounts = runCatching { accountRepo.queryActiveAccounts() }.getOrElse { return Result.retry() }
        if (accounts.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val settings = AppSettings()
        var staleCount = 0

        for (account in accounts) {
            if (account.isArchived) continue
            val config = runCatching {
                reminderSettingsRepo.getReminderConfig(account.id)
            }.getOrNull() ?: BalanceUpdateReminderConfig()

            val isStale = AccountStatusCalculator.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = now,
                zoneId = ZoneId.systemDefault(),
            )
            if (!isStale) continue

            staleCount++
            val balance = runCatching {
                container.calculateCurrentBalanceUseCase(account.id)
            }.getOrDefault(0L)
            val balanceText = AmountFormatter.format(balance, settings)
            val configText = config.displayText

            ReminderNotifications.postBalanceCheckReminder(
                context = appContext,
                accountId = account.id,
                accountName = account.name,
                balanceText = balanceText,
                reminderScheduleText = configText,
            )
        }

        return Result.success()
    }
}
