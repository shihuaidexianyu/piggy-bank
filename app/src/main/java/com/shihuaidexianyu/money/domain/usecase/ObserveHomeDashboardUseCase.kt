package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.time.clockMinuteTickerFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class HomeDashboardSnapshot(
    val settings: PortableSettings,
    val totalAssets: Long,
    val periodBreakdown: PeriodAssetBreakdown,
    val periodRecordCount: Int,
    val staleAccountCount: Int,
    val openAccounts: List<Account>,
    val staleAccounts: List<Account>,
    val accountBalances: Map<Long, Long>,
    val dueReminders: List<RecurringReminder>,
    val recentRecords: List<HistoryRecord>,
    val hasAnyAccounts: Boolean,
    val allAccountCount: Int,
    /** Which period the aggregates above cover. */
    val period: DashboardPeriod = DashboardPeriod.DEFAULT,
    /** Whether any account (open or closed) is an investment account — gates investment UI. */
    val hasInvestmentAccounts: Boolean = false,
    /** Current balance summed over investment accounts; funding assets = total − this. */
    val investmentAssets: Long = 0L,
    /** Reconciliation deltas on investment accounts within the selected period. */
    val periodInvestmentPnl: Long = 0L,
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
        get() = ledgerSubtractExact(cashInflow, cashOutflow)

    val manualAdjustmentNet: Long
        get() = ledgerSubtractExact(manualAdjustmentIncrease, manualAdjustmentDecrease)

    val reconciliationNet: Long
        get() = ledgerSubtractExact(reconciliationIncrease, reconciliationDecrease)
}

class ObserveHomeDashboardUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val timeSignal: Flow<Long> = clockMinuteTickerFlow(clockProvider),
) {
    /**
     * @param periodSignal the period the user selected on the dashboard. Defaults to a constant
     * flow so callers that do not offer a switcher keep the previous month-scoped behaviour.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        periodSignal: Flow<DashboardPeriod> = flowOf(DashboardPeriod.DEFAULT),
    ): Flow<HomeDashboardSnapshot> {
        val accountInvalidation = combine(
            accountRepository.observeAllAccounts(),
            accountRepository.observeOpenAccounts(),
        ) { _, _ -> Unit }
        val dashboardInvalidation = combine(
            accountInvalidation,
            accountReminderSettingsRepository.observeReminderConfigs(),
            portableSettingsRepository.observe(),
            transactionRepository.observeChangeVersion(),
            recurringReminderRepository.observeDueReminders(),
        ) { _, _, _, _, _ -> Unit }
        return combine(dashboardInvalidation, timeSignal, periodSignal) { _, snapshotTimeMillis, period ->
            snapshotTimeMillis to period
        }.mapLatest { (snapshotTimeMillis, period) ->
            val input = readConsistentInput(snapshotTimeMillis, period)
            HomeProjector.project(
                accounts = input.allAccounts,
                openAccounts = input.openAccounts,
                reminderConfigs = input.reminderConfigs,
                settings = input.settings,
                dueReminders = input.dueReminders,
                balances = input.balances,
                openingBalanceByAccount = input.openingBalanceByAccount,
                newAccountOpeningAssets = input.newAccountOpeningAssets,
                cashInflow = input.cashInflow,
                cashOutflow = input.cashOutflow,
                reconciliationIncrease = input.reconciliationIncrease,
                reconciliationDecrease = input.reconciliationDecrease,
                manualAdjustmentIncrease = input.manualAdjustmentIncrease,
                manualAdjustmentDecrease = input.manualAdjustmentDecrease,
                cashFlowRecordCount = input.cashFlowRecordCount,
                transferRecordCount = input.transferRecordCount,
                manualAdjustmentRecordCount = input.manualAdjustmentRecordCount,
                recentRecords = input.recentRecords,
                period = period,
                reconciliationNetByAccount = input.reconciliationNetByAccount,
                snapshotTimeMillis = snapshotTimeMillis,
                zoneId = input.zoneId,
            )
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun readConsistentInput(
        snapshotTimeMillis: Long,
        period: DashboardPeriod,
    ): HomeDashboardInput {
        val zoneId = zoneIdProvider.zoneId()
        val range = TimeRangeCalculator.rangeFor(period, zoneId, snapshotTimeMillis)
        return transactionRepository.runInTransaction {
            val allAccounts = accountRepository.queryAllAccounts()
            val settings = portableSettingsRepository.query()
            val openingAccounts = allAccounts.filter {
                LedgerBalanceCalculator.openingAt(it) < range.startInclusive
            }
            val periodSummary = transactionRepository.queryHomePeriodLedgerSummary(
                range.startInclusive,
                range.endExclusive,
            )
            val balances = calculateAccountBalancesUseCase(allAccounts, snapshotTimeMillis)
            // Only read the per-account breakdown when it can matter — accounts without any
            // investment kind produce no investment P&L by definition.
            val reconciliationNetByAccount = if (allAccounts.any(Account::isInvestment)) {
                transactionRepository.queryReconciliationNetByAccount(
                    range.startInclusive,
                    range.endExclusive,
                )
            } else {
                emptyMap()
            }
            HomeDashboardInput(
                zoneId = zoneId,
                reconciliationNetByAccount = reconciliationNetByAccount,
                allAccounts = allAccounts,
                openAccounts = accountRepository.queryOpenAccounts(),
                reminderConfigs = accountReminderSettingsRepository.queryReminderConfigs(),
                settings = settings,
                dueReminders = recurringReminderRepository.queryDue(snapshotTimeMillis),
                balances = balances,
                openingBalanceByAccount = calculateAccountBalancesUseCase.before(
                    openingAccounts,
                    range.startInclusive,
                ),
                newAccountOpeningAssets = allAccounts
                    .filter { account ->
                        LedgerBalanceCalculator.isOpeningInRange(
                            account,
                            range.startInclusive,
                            range.endExclusive,
                        )
                    }
                    .map(Account::initialBalance)
                    .ledgerSumExact(),
                cashInflow = periodSummary.cashInflow,
                cashOutflow = periodSummary.cashOutflow,
                reconciliationIncrease = periodSummary.reconciliationIncrease,
                reconciliationDecrease = periodSummary.reconciliationDecrease,
                manualAdjustmentIncrease = periodSummary.manualAdjustmentIncrease,
                manualAdjustmentDecrease = periodSummary.manualAdjustmentDecrease,
                cashFlowRecordCount = periodSummary.cashFlowRecordCount,
                transferRecordCount = periodSummary.transferRecordCount,
                manualAdjustmentRecordCount = periodSummary.manualAdjustmentRecordCount,
                recentRecords = transactionRepository.queryHistoryRecords(
                    filters = HistoryRecordFilters(),
                    cursor = null,
                    limit = HOME_RECENT_RECORD_LIMIT,
                ),
            )
        }
    }
}

private const val HOME_RECENT_RECORD_LIMIT = 5

private data class HomeDashboardInput(
    val zoneId: java.time.ZoneId,
    val reconciliationNetByAccount: Map<Long, Long>,
    val allAccounts: List<Account>,
    val openAccounts: List<Account>,
    val reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    val settings: PortableSettings,
    val dueReminders: List<RecurringReminder>,
    val balances: Map<Long, Long>,
    val openingBalanceByAccount: Map<Long, Long>,
    val newAccountOpeningAssets: Long,
    val cashInflow: Long,
    val cashOutflow: Long,
    val reconciliationIncrease: Long,
    val reconciliationDecrease: Long,
    val manualAdjustmentIncrease: Long,
    val manualAdjustmentDecrease: Long,
    val cashFlowRecordCount: Int,
    val transferRecordCount: Int,
    val manualAdjustmentRecordCount: Int,
    val recentRecords: List<HistoryRecord>,
)
