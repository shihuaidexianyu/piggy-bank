package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowAnalysisEntry
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class StatsDailyPoint(
    val date: LocalDate,
    val inflow: Long,
    val outflow: Long,
    val netFlow: Long,
    val historyFilters: HistoryRecordFilters,
    val inflowHistoryFilters: HistoryRecordFilters,
    val outflowHistoryFilters: HistoryRecordFilters,
)

data class StatsAccountCashFlow(
    val accountId: Long,
    val name: String,
    val inflow: Long,
    val outflow: Long,
    val inflowHistoryFilters: HistoryRecordFilters,
    val outflowHistoryFilters: HistoryRecordFilters,
)

data class StatsTransferPath(
    val fromAccountId: Long,
    val fromAccountName: String,
    val toAccountId: Long,
    val toAccountName: String,
    val amount: Long,
    val historyFilters: HistoryRecordFilters,
)

data class StatsDashboardSnapshot(
    val settings: PortableSettings,
    val period: StatsPeriod,
    val range: TimeRange,
    val zoneId: ZoneId,
    val hasSourceAccounts: Boolean,
    val openingAssets: Long,
    val closingAssets: Long,
    val assetAdjustment: Long,
    val totalInflow: Long,
    val totalOutflow: Long,
    val netCashFlow: Long,
    val dailyPoints: List<StatsDailyPoint>,
    val accountCashFlows: List<StatsAccountCashFlow>,
    val transferPaths: List<StatsTransferPath>,
    val inflowHistoryFilters: HistoryRecordFilters,
    val outflowHistoryFilters: HistoryRecordFilters,
    val netCashFlowHistoryFilters: HistoryRecordFilters,
)

class ObserveStatsDashboardUseCase(
    private val accountRepository: AccountRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val zoneIdProvider: ZoneIdProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(selectionFlow: Flow<StatsRangeSelection>): Flow<StatsDashboardSnapshot> = combine(
        accountRepository.observeAllAccounts(),
        portableSettingsRepository.observe(),
        transactionRepository.observeChangeVersion(),
        selectionFlow,
    ) { _, _, _, selection -> selection }
        .mapLatest { selection ->
            val source = readConsistentSource(selection)
            StatsProjector.project(
                accounts = source.accounts,
                settings = source.settings,
                selection = source.selection,
                range = source.range,
                cashEntries = source.cashEntries,
                zoneId = source.zoneId,
                openingAssets = source.openingAssets,
                closingAssets = source.closingAssets,
            )
        }
        .flowOn(Dispatchers.Default)

    private suspend fun readConsistentSource(selection: StatsRangeSelection): StatsSource {
        // Capture once so range calculation, DST grouping, labels and drill-down boundaries agree.
        val zoneId = zoneIdProvider.zoneId()
        val monthlySelection = selection.copy(period = StatsPeriod.MONTH)
        val range = TimeRangeCalculator.statsRange(StatsPeriod.MONTH, zoneId, monthlySelection.anchorMillis)
        return transactionRepository.runInTransaction {
            val accounts = accountRepository.queryAllAccounts()
            val openingAccounts = accounts.filter {
                LedgerBalanceCalculator.openingAt(it) < range.startInclusive
            }
            val openingAssets = ledgerAddExact(
                calculateAccountBalancesUseCase.before(
                    openingAccounts,
                    range.startInclusive,
                ).values.ledgerSumExact(),
                accounts.filter { account ->
                    LedgerBalanceCalculator.isOpeningInRange(
                        account,
                        range.startInclusive,
                        range.endExclusive,
                    )
                }
                    .map(Account::initialBalance)
                    .ledgerSumExact(),
            )
            StatsSource(
                accounts = accounts,
                settings = portableSettingsRepository.query(),
                selection = monthlySelection,
                range = range,
                cashEntries = transactionRepository.queryCashFlowAnalysisEntriesBetween(
                    range.startInclusive,
                    range.endExclusive,
                ),
                openingAssets = openingAssets,
                closingAssets = calculateAccountBalancesUseCase.before(
                    accounts,
                    range.endExclusive,
                ).values.ledgerSumExact(),
                zoneId = zoneId,
            )
        }
    }
}

private data class StatsSource(
    val accounts: List<Account>,
    val settings: PortableSettings,
    val selection: StatsRangeSelection,
    val range: TimeRange,
    val cashEntries: List<CashFlowAnalysisEntry>,
    val openingAssets: Long,
    val closingAssets: Long,
    val zoneId: ZoneId,
)
