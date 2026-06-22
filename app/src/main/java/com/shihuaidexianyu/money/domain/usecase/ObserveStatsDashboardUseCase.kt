package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class StatsPurposeBreakdown(
    val purpose: String,
    val amount: Long,
)

data class StatsDailyPoint(
    val date: LocalDate,
    val inflow: Long,
    val outflow: Long,
)

data class StatsAccountBalance(
    val accountId: Long,
    val name: String,
    val colorName: String,
    val balance: Long,
)

data class StatsDashboardSnapshot(
    val settings: AppSettings,
    val period: StatsPeriod,
    val range: TimeRange,
    val openingAssets: Long,
    val closingAssets: Long,
    val totalInflow: Long,
    val totalOutflow: Long,
    val netCashFlow: Long,
    val assetChange: Long,
    val assetAdjustment: Long,
    val manualAdjustmentNet: Long,
    val reconciliationNet: Long,
    val breakdown: PeriodAssetBreakdown,
    val purposeBreakdown: List<StatsPurposeBreakdown>,
    val dailyPoints: List<StatsDailyPoint>,
    val accountBalances: List<StatsAccountBalance>,
)

class ObserveStatsDashboardUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(selectionFlow: Flow<StatsRangeSelection>): Flow<StatsDashboardSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
            selectionFlow,
        ) { accounts, settings, _, selection ->
            StatsSource(accounts, settings, selection)
        }.mapLatest { source ->
            buildSnapshot(source.accounts, source.settings, source.selection)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        accounts: List<Account>,
        settings: AppSettings,
        selection: StatsRangeSelection,
    ): StatsDashboardSnapshot = coroutineScope {
        val zoneId = ZoneId.systemDefault()
        val range = TimeRangeCalculator.statsRange(selection.period, zoneId, selection.anchorMillis)
        val startBaselineAt = (range.startAtMillis - 1L).coerceAtLeast(0L)
        val zoneOffsetSeconds = zoneId.rules.getOffset(Instant.ofEpochMilli(range.startAtMillis)).totalSeconds
        val inflowTotalsJob = async {
            transactionRepository.queryPurposeTotals(
                direction = CashFlowDirection.INFLOW.value,
                startAt = range.startAtMillis,
                endAt = range.endAtMillis,
            )
        }
        val purposeBreakdownJob = async {
            transactionRepository.queryPurposeTotals(
                direction = CashFlowDirection.OUTFLOW.value,
                startAt = range.startAtMillis,
                endAt = range.endAtMillis,
            )
        }
        val dailyTotalsJob = async {
            transactionRepository.queryDailyCashFlowTotals(
                startAt = range.startAtMillis,
                endAt = range.endAtMillis,
                zoneOffsetSeconds = zoneOffsetSeconds,
            )
        }
        val manualAdjustmentIncreaseJob = async {
            transactionRepository.sumManualAdjustmentIncreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val manualAdjustmentDecreaseJob = async {
            transactionRepository.sumManualAdjustmentDecreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val reconciliationIncreaseJob = async {
            transactionRepository.sumBalanceUpdateIncreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val reconciliationDecreaseJob = async {
            transactionRepository.sumBalanceUpdateDecreaseBetween(range.startAtMillis, range.endAtMillis)
        }
        val balanceJob = async { calculateAccountBalancesUseCase(accounts, range.endAtMillis) }
        val openingBalanceJobs = accounts
            .filter { LedgerBalanceCalculator.isOpenAt(it, startBaselineAt) }
            .map { account ->
                account.id to async { calculateCurrentBalanceUseCase(account.id, startBaselineAt) }
            }
        val newAccountOpeningAssets = accounts
            .filter { account -> LedgerBalanceCalculator.isOpeningInRange(account, range.startAtMillis, range.endAtMillis) }
            .sumOf(Account::initialBalance)

        val balances = balanceJob.await()
        val openingBalanceByAccount = openingBalanceJobs.toMap().mapValues { it.value.await() }

        StatsProjector.project(
            accounts = accounts,
            settings = settings,
            selection = selection,
            range = range,
            balances = balances,
            openingBalanceByAccount = openingBalanceByAccount,
            newAccountOpeningAssets = newAccountOpeningAssets,
            inflowTotals = inflowTotalsJob.await(),
            outflowTotals = purposeBreakdownJob.await(),
            dailyTotals = dailyTotalsJob.await(),
            manualAdjustmentIncrease = manualAdjustmentIncreaseJob.await(),
            manualAdjustmentDecrease = manualAdjustmentDecreaseJob.await(),
            reconciliationIncrease = reconciliationIncreaseJob.await(),
            reconciliationDecrease = reconciliationDecreaseJob.await(),
            zoneId = zoneId,
        )
    }
}

private data class StatsSource(
    val accounts: List<Account>,
    val settings: AppSettings,
    val selection: StatsRangeSelection,
)
