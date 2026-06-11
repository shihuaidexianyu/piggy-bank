package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class HomeDashboardSnapshot(
    val settings: AppSettings,
    val totalAssets: Long,
    val periodBreakdown: PeriodAssetBreakdown,
    val staleAccountCount: Int,
    val activeAccounts: List<Account>,
    val staleAccounts: List<Account>,
    val accountBalances: Map<Long, Long>,
    val dueReminders: List<RecurringReminder>,
)

data class PeriodAssetBreakdown(
    val openingAssets: Long,
    val closingAssets: Long,
    val assetChange: Long,
    val cashInflow: Long,
    val cashOutflow: Long,
    val manualAdjustmentIncrease: Long,
    val manualAdjustmentDecrease: Long,
    val reconciliationIncrease: Long,
    val reconciliationDecrease: Long,
) {
    val cashNet: Long
        get() = cashInflow - cashOutflow

    val manualAdjustmentNet: Long
        get() = manualAdjustmentIncrease - manualAdjustmentDecrease

    val reconciliationNet: Long
        get() = reconciliationIncrease - reconciliationDecrease
}

class ObserveHomeDashboardUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeDashboardSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            accountReminderSettingsRepository.observeReminderConfigs(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
            recurringReminderRepository.observeDueReminders(),
        ) { accounts, reminderConfigs, settings, _, dueReminders ->
            Triple(accounts, reminderConfigs, settings) to dueReminders
        }.mapLatest { (triple, dueReminders) ->
            val (accounts, reminderConfigs, settings) = triple
            buildSnapshot(accounts, reminderConfigs, settings, dueReminders)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
        dueReminders: List<RecurringReminder>,
    ): HomeDashboardSnapshot = coroutineScope {
        val range = TimeRangeUtils.currentRange(settings.homePeriod)
        val periodStartBaselineAt = (range.startAtMillis - 1L).coerceAtLeast(0L)
        val balanceJob = async { calculateAccountBalancesUseCase(accounts) }
        val openingBalanceJobs = accounts
            .filter { LedgerBalanceCalculator.isOpenAt(it, periodStartBaselineAt) }
            .map { account ->
                async { calculateCurrentBalanceUseCase(account.id, periodStartBaselineAt) }
            }
        val newAccountOpeningAssets = accounts
            .filter { account -> LedgerBalanceCalculator.isOpeningInRange(account, range.startAtMillis, range.endAtMillis) }
            .sumOf(Account::initialBalance)
        val cashInflowJob = async { transactionRepository.sumCashInflowBetween(range.startAtMillis, range.endAtMillis) }
        val cashOutflowJob = async { transactionRepository.sumCashOutflowBetween(range.startAtMillis, range.endAtMillis) }
        val reconciliationIncreaseJob = async {
            transactionRepository.sumBalanceUpdateIncreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val reconciliationDecreaseJob = async {
            transactionRepository.sumBalanceUpdateDecreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val manualAdjustmentIncreaseJob = async {
            transactionRepository.sumManualAdjustmentIncreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val manualAdjustmentDecreaseJob = async {
            transactionRepository.sumManualAdjustmentDecreaseBetween(range.startAtMillis, range.endAtMillis)
        }

        val balances = balanceJob.await()
        val totalAssets = balances.values.sum()
        val openingTotalAssets = openingBalanceJobs.sumOf { it.await() } + newAccountOpeningAssets
        val periodBreakdown = PeriodAssetBreakdown(
            openingAssets = openingTotalAssets,
            closingAssets = totalAssets,
            assetChange = totalAssets - openingTotalAssets,
            cashInflow = cashInflowJob.await(),
            cashOutflow = cashOutflowJob.await(),
            manualAdjustmentIncrease = manualAdjustmentIncreaseJob.await(),
            manualAdjustmentDecrease = manualAdjustmentDecreaseJob.await(),
            reconciliationIncrease = reconciliationIncreaseJob.await(),
            reconciliationDecrease = reconciliationDecreaseJob.await(),
        )
        val staleAccounts = accounts.filter { account ->
            AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
            )
        }
        HomeDashboardSnapshot(
            settings = settings,
            totalAssets = totalAssets,
            periodBreakdown = periodBreakdown,
            staleAccountCount = staleAccounts.size,
            activeAccounts = accounts,
            staleAccounts = staleAccounts,
            accountBalances = balances,
            dueReminders = dueReminders,
        )
    }
}
