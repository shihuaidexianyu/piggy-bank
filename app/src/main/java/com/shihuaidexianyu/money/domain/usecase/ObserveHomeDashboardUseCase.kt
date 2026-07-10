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
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
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
    val periodRecordCount: Int,
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
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
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
        val range = TimeRangeCalculator.currentRange(
            period = settings.homePeriod,
            zoneId = zoneIdProvider.zoneId(),
            nowMillis = clockProvider.nowMillis(),
        )
        val balanceJob = async { calculateAccountBalancesUseCase(accounts) }
        val openingBalanceJobs = accounts
            .filter { LedgerBalanceCalculator.openingAt(it) < range.startInclusive }
            .map { account ->
                account.id to async { calculateCurrentBalanceUseCase.before(account.id, range.startInclusive) }
            }
        val newAccountOpeningAssets = accounts
            .filter { account -> LedgerBalanceCalculator.isOpeningInRange(account, range.startInclusive, range.endExclusive) }
            .sumOf(Account::initialBalance)
        val cashInflowJob = async { transactionRepository.sumCashInflowBetween(range.startInclusive, range.endExclusive) }
        val cashOutflowJob = async { transactionRepository.sumCashOutflowBetween(range.startInclusive, range.endExclusive) }
        val reconciliationIncreaseJob = async {
            transactionRepository.sumBalanceUpdateIncreaseBetween(range.startInclusive, range.endExclusive)
        }
        val reconciliationDecreaseJob = async {
            transactionRepository.sumBalanceUpdateDecreaseBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentIncreaseJob = async {
            transactionRepository.sumManualAdjustmentIncreaseBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentDecreaseJob = async {
            transactionRepository.sumManualAdjustmentDecreaseBetween(range.startInclusive, range.endExclusive)
        }
        val cashFlowRecordCountJob = async {
            transactionRepository.countActiveCashFlowRecordsBetween(range.startInclusive, range.endExclusive)
        }
        val transferRecordCountJob = async {
            transactionRepository.countActiveTransferRecordsBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentRecordCountJob = async {
            transactionRepository.countManualAdjustmentRecordsBetween(range.startInclusive, range.endExclusive)
        }

        val balances = balanceJob.await()
        val openingBalanceByAccount = openingBalanceJobs.toMap().mapValues { it.value.await() }

        HomeProjector.project(
            accounts = accounts,
            reminderConfigs = reminderConfigs,
            settings = settings,
            dueReminders = dueReminders,
            balances = balances,
            openingBalanceByAccount = openingBalanceByAccount,
            newAccountOpeningAssets = newAccountOpeningAssets,
            cashInflow = cashInflowJob.await(),
            cashOutflow = cashOutflowJob.await(),
            reconciliationIncrease = reconciliationIncreaseJob.await(),
            reconciliationDecrease = reconciliationDecreaseJob.await(),
            manualAdjustmentIncrease = manualAdjustmentIncreaseJob.await(),
            manualAdjustmentDecrease = manualAdjustmentDecreaseJob.await(),
            cashFlowRecordCount = cashFlowRecordCountJob.await(),
            transferRecordCount = transferRecordCountJob.await(),
            manualAdjustmentRecordCount = manualAdjustmentRecordCountJob.await(),
        )
    }
}
