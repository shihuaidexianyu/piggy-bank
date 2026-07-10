package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.AccountStatusCalculator
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.ZoneId

internal suspend fun calculateBalanceCheckBalances(
    accounts: List<Account>,
    calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
): Map<Long, Long> = calculateAccountBalancesUseCase(accounts)

/**
 * Periodic [CoroutineWorker] that checks all open accounts for stale balance-update status.
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
        if (container.startupMigrationCoordinator.withReadyLedgerAccess { true } == null) {
            return Result.retry()
        }
        val accountRepo = container.accountRepository
        val reminderSettingsRepo = container.accountReminderSettingsRepository

        val accounts = runCatching { accountRepo.queryOpenAccounts() }.getOrElse { return Result.retry() }
        if (accounts.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val settings = container.portableSettingsRepository.query()
        val staleAccounts = accounts.mapNotNull { account ->
            if (account.isClosed) return@mapNotNull null
            val config = runCatching {
                reminderSettingsRepo.getReminderConfig(account.id)
            }.getOrNull() ?: BalanceUpdateReminderConfig()

            if (!config.isEnabled) return@mapNotNull null
            val isStale = AccountStatusCalculator.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = now,
                zoneId = ZoneId.systemDefault(),
            )
            if (isStale) account to config else null
        }
        if (staleAccounts.isEmpty()) return Result.success()
        val balances = runCatching {
            calculateBalanceCheckBalances(
                staleAccounts.map { it.first },
                container.calculateAccountBalancesUseCase,
            )
        }.getOrDefault(emptyMap())

        for ((account, config) in staleAccounts) {
            val balance = balances[account.id] ?: 0L
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
