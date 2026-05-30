package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.TimeRange
import com.shihuaidexianyu.money.util.TimeRangeUtils
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
    val purposeBreakdown: List<StatsPurposeBreakdown>,
    val dailyPoints: List<StatsDailyPoint>,
    val accountBalances: List<StatsAccountBalance>,
)

class ObserveStatsDashboardUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
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
        val range = TimeRangeUtils.statsRange(selection.period, zoneId, selection.anchorMillis)
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
        val balanceJobs = accounts.map { account ->
            async {
                account to calculateCurrentBalanceUseCase(account.id, range.endAtMillis)
            }
        }
        val openingBalanceJobs = accounts
            .filter { it.createdAt <= startBaselineAt }
            .map { account ->
                async { calculateCurrentBalanceUseCase(account.id, startBaselineAt) }
            }

        val inflowTotals = inflowTotalsJob.await()
        val outflowTotals = purposeBreakdownJob.await()
        val totalInflow = inflowTotals.sumOf { it.amount }
        val totalOutflow = outflowTotals.sumOf { it.amount }
        val accountBalances = balanceJobs.map { it.await() }.map { (account, balance) ->
            StatsAccountBalance(
                accountId = account.id,
                name = account.name,
                colorName = account.colorName,
                balance = balance,
            )
        }.sortedByDescending { it.balance }
        val currentAssets = accountBalances.sumOf { it.balance }
        val openingAssets = openingBalanceJobs.sumOf { it.await() }
        val netCashFlow = totalInflow - totalOutflow
        val assetChange = currentAssets - openingAssets

        StatsDashboardSnapshot(
            settings = settings,
            period = selection.period,
            range = range,
            openingAssets = openingAssets,
            closingAssets = currentAssets,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            assetChange = assetChange,
            assetAdjustment = assetChange - netCashFlow,
            purposeBreakdown = outflowTotals.map {
                StatsPurposeBreakdown(purpose = it.purpose, amount = it.amount)
            },
            dailyPoints = buildDailyPoints(dailyTotalsJob.await(), range, zoneId),
            accountBalances = accountBalances,
        )
    }

    private fun buildDailyPoints(
        dailyTotals: List<CashFlowDailyTotal>,
        range: TimeRange,
        zoneId: ZoneId,
    ): List<StatsDailyPoint> {
        val startDate = Instant.ofEpochMilli(range.startAtMillis).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(range.endAtMillis).atZone(zoneId).toLocalDate()
        val inflowByDate = dailyTotals
            .filter { it.direction == CashFlowDirection.INFLOW.value }
            .associate { LocalDate.ofEpochDay(it.epochDay) to it.amount }
        val outflowByDate = dailyTotals
            .filter { it.direction == CashFlowDirection.OUTFLOW.value }
            .associate { LocalDate.ofEpochDay(it.epochDay) to it.amount }
        return generateSequence(startDate) { date ->
            date.plusDays(1).takeIf { !it.isAfter(endDate) }
        }.map { date ->
            StatsDailyPoint(
                date = date,
                inflow = inflowByDate[date] ?: 0L,
                outflow = outflowByDate[date] ?: 0L,
            )
        }.toList()
    }
}

private data class StatsSource(
    val accounts: List<Account>,
    val settings: AppSettings,
    val selection: StatsRangeSelection,
)
