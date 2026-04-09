package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.TimeRange
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.max

data class CashFlowBar(
    val label: String,
    val inflow: Long,
    val outflow: Long,
)

data class CashFlowEvent(
    val occurredAt: Long,
    val inflow: Long,
    val outflow: Long,
) {
    val netAmount: Long
        get() = inflow - outflow
}

data class NetAssetPoint(
    val label: String,
    val timestamp: Long,
    val totalBalance: Long,
)

data class AccountShare(
    val accountName: String,
    val groupType: AccountGroupType,
    val balance: Long,
)

data class AssetGroupShare(
    val groupType: AccountGroupType,
    val balance: Long,
    val accountCount: Int,
)

data class InvestmentPoint(
    val label: String,
    val timestamp: Long,
    val pnl: Long,
    val returnRate: Double,
)

data class StatsOverview(
    val totalInflow: Long,
    val totalOutflow: Long,
    val netCashFlow: Long,
    val currentNetAssets: Long,
    val netAssetDelta: Long,
    val activeAccountCount: Int,
    val activeInvestmentAccountCount: Int,
)

data class StatsIntervalSummary(
    val label: String,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val inflow: Long,
    val outflow: Long,
    val netCashFlow: Long,
    val endNetAssets: Long,
    val investmentPnl: Long,
    val investmentReturnRate: Double?,
    val investmentSettlementCount: Int,
)

data class InvestmentOverview(
    val totalPnl: Long,
    val weightedReturnRate: Double?,
    val netTransferIn: Long,
    val netTransferOut: Long,
    val settlementCount: Int,
)

data class StatsSnapshot(
    val settings: AppSettings,
    val period: StatsPeriod,
    val overview: StatsOverview,
    val intervals: List<StatsIntervalSummary>,
    val assetGroupShares: List<AssetGroupShare>,
    val topAccountShares: List<AccountShare>,
    val investmentOverview: InvestmentOverview,
    val cashFlowEvents: List<CashFlowEvent>,
    val cashFlowBars: List<CashFlowBar>,
    val netAssetPoints: List<NetAssetPoint>,
    val investmentPoints: List<InvestmentPoint>,
)

class ObserveStatsUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    @Suppress("OPT_IN_USAGE")
    fun invoke(periodFlow: Flow<StatsPeriod>): Flow<StatsSnapshot> {
        return combine(
            periodFlow,
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
        ) { period, settings, _ -> period to settings }
            .flatMapLatest { (period, settings) ->
                flow { emit(buildSnapshot(period, settings)) }
            }
    }

    private suspend fun buildSnapshot(
        period: StatsPeriod,
        settings: AppSettings,
    ): StatsSnapshot = coroutineScope {
        val now = nowProvider()
        val subPeriods = TimeRangeUtils.splitIntoPeriods(period, nowMillis = now)
        val overallRange = TimeRangeUtils.currentStatsRange(period, nowMillis = now)
        val statsData = loadStatsData(overallRange)

        val intervalsJob = async { buildIntervalSummaries(subPeriods, statsData) }
        val groupSharesJob = async { buildAssetGroupShares(statsData) }
        val topAccountsJob = async { buildTopAccountShares(statsData) }
        val investmentOverviewJob = async { buildInvestmentOverview(statsData) }
        val cashFlowEventsJob = async { buildCashFlowEvents(statsData.allCashFlows) }

        val intervals = intervalsJob.await()
        StatsSnapshot(
            settings = settings,
            period = period,
            overview = buildOverview(statsData),
            intervals = intervals,
            assetGroupShares = groupSharesJob.await(),
            topAccountShares = topAccountsJob.await(),
            investmentOverview = investmentOverviewJob.await(),
            cashFlowEvents = cashFlowEventsJob.await(),
            cashFlowBars = intervals.map { interval ->
                CashFlowBar(
                    label = interval.label,
                    inflow = interval.inflow,
                    outflow = interval.outflow,
                )
            },
            netAssetPoints = intervals.map { interval ->
                NetAssetPoint(
                    label = interval.label,
                    timestamp = interval.endAtMillis,
                    totalBalance = interval.endNetAssets,
                )
            },
            investmentPoints = intervals
                .filter { it.investmentSettlementCount > 0 }
                .map { interval ->
                    InvestmentPoint(
                        label = interval.label,
                        timestamp = interval.endAtMillis,
                        pnl = interval.investmentPnl,
                        returnRate = interval.investmentReturnRate ?: 0.0,
                    )
                },
        )
    }

    private suspend fun loadStatsData(
        overallRange: TimeRange,
    ): StatsData = coroutineScope {
        val accounts = accountRepository.queryActiveAccounts()
        val rangeStart = overallRange.startAtMillis
        val rangeEnd = overallRange.endAtMillis

        val cashFlowsJob = async {
            transactionRepository.queryActiveCashFlowRecordsBetween(rangeStart, rangeEnd)
        }
        val allCashFlowsJob = async {
            transactionRepository.queryAllActiveCashFlowRecords()
        }
        val transfersJob = async {
            transactionRepository.queryActiveTransferRecordsBetween(rangeStart, rangeEnd)
        }
        val adjustmentsJob = async {
            transactionRepository.queryManualBalanceAdjustmentRecordsBetween(rangeStart, rangeEnd)
        }
        val balanceUpdatesJob = async {
            transactionRepository.queryBalanceUpdateRecordsBetween(rangeStart, rangeEnd)
        }
        val settlementsJob = async { transactionRepository.queryInvestmentSettlementsBetween(rangeStart, rangeEnd) }
        val currentBalancesJob = async {
            accounts.associate { account -> account.id to calculateCurrentBalanceUseCase(account.id) }
        }
        val rangeStartBalancesJob = async {
            accounts.associate { account ->
                val balance = if (rangeStart <= account.createdAt) {
                    0L
                } else {
                    calculateCurrentBalanceUseCase(account.id, rangeStart - 1L)
                }
                account.id to balance
            }
        }

        StatsData(
            overallRange = overallRange,
            accounts = accounts,
            cashFlowsInRange = cashFlowsJob.await(),
            allCashFlows = allCashFlowsJob.await(),
            transfersInRange = transfersJob.await(),
            manualAdjustmentsInRange = adjustmentsJob.await(),
            balanceUpdatesInRange = balanceUpdatesJob.await(),
            allInvestmentSettlements = settlementsJob.await(),
            currentBalances = currentBalancesJob.await(),
            rangeStartBalances = rangeStartBalancesJob.await(),
        )
    }

    private fun buildOverview(
        statsData: StatsData,
    ): StatsOverview {
        val totalInflow = statsData.cashFlowsInRange
            .filter { it.direction == "inflow" }
            .sumOf { it.amount }
        val totalOutflow = statsData.cashFlowsInRange
            .filter { it.direction == "outflow" }
            .sumOf { it.amount }
        val currentNetAssets = statsData.currentBalances.values.sum()
        val rangeStartNetAssets = statsData.rangeStartBalances.values.sum()
        return StatsOverview(
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = totalInflow - totalOutflow,
            currentNetAssets = currentNetAssets,
            netAssetDelta = currentNetAssets - rangeStartNetAssets,
            activeAccountCount = statsData.accounts.size,
            activeInvestmentAccountCount = statsData.accounts.count {
                AccountGroupType.fromValue(it.groupType) == AccountGroupType.INVESTMENT
            },
        )
    }

    private fun buildIntervalSummaries(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
        statsData: StatsData,
    ): List<StatsIntervalSummary> {
        if (subPeriods.isEmpty()) return emptyList()

        val inflowTotals = LongArray(subPeriods.size)
        val outflowTotals = LongArray(subPeriods.size)
        statsData.cashFlowsInRange.forEach { record ->
            val index = resolveSubPeriodIndex(subPeriods, record.occurredAt)
            if (index < 0) return@forEach
            when (record.direction) {
                "inflow" -> inflowTotals[index] += record.amount
                "outflow" -> outflowTotals[index] += record.amount
            }
        }

        val investmentAccountIds = statsData.accounts
            .filter { AccountGroupType.fromValue(it.groupType) == AccountGroupType.INVESTMENT }
            .map { it.id }
            .toSet()
        val investmentPnlTotals = LongArray(subPeriods.size)
        val investmentBaseTotals = LongArray(subPeriods.size)
        val investmentCounts = IntArray(subPeriods.size)
        statsData.allInvestmentSettlements
            .filter { it.accountId in investmentAccountIds }
            .forEach { settlement ->
                val index = resolveSubPeriodIndex(subPeriods, settlement.periodEndAt)
                if (index < 0) return@forEach
                investmentPnlTotals[index] += settlement.pnl
                investmentBaseTotals[index] += max(
                    settlement.previousBalance + settlement.netTransferIn - settlement.netTransferOut,
                    1L,
                )
                investmentCounts[index] += 1
            }

        val eventsByAccount = buildBalanceEventsByAccount(statsData)
        val runningBalances = statsData.rangeStartBalances.toMutableMap()
        val pointers = statsData.accounts.associate { it.id to 0 }.toMutableMap()

        return subPeriods.mapIndexed { index, subPeriod ->
            var totalAssets = 0L
            for (account in statsData.accounts) {
                val events = eventsByAccount[account.id].orEmpty()
                var pointer = pointers[account.id] ?: 0
                var balance = runningBalances[account.id] ?: 0L
                while (pointer < events.size && events[pointer].occurredAt <= subPeriod.range.endAtMillis) {
                    balance = events[pointer].applyTo(balance)
                    pointer += 1
                }
                pointers[account.id] = pointer
                runningBalances[account.id] = balance
                totalAssets += balance
            }

            val investmentRate = if (investmentCounts[index] > 0) {
                investmentPnlTotals[index].toDouble() / investmentBaseTotals[index].toDouble()
            } else {
                null
            }

            StatsIntervalSummary(
                label = subPeriod.label,
                startAtMillis = subPeriod.range.startAtMillis,
                endAtMillis = subPeriod.range.endAtMillis,
                inflow = inflowTotals[index],
                outflow = outflowTotals[index],
                netCashFlow = inflowTotals[index] - outflowTotals[index],
                endNetAssets = totalAssets,
                investmentPnl = investmentPnlTotals[index],
                investmentReturnRate = investmentRate,
                investmentSettlementCount = investmentCounts[index],
            )
        }
    }

    private fun buildAssetGroupShares(
        statsData: StatsData,
    ): List<AssetGroupShare> {
        return statsData.accounts
            .groupBy { AccountGroupType.fromValue(it.groupType) }
            .mapNotNull { (groupType, accounts) ->
                val balance = accounts.sumOf { account -> statsData.currentBalances[account.id] ?: 0L }
                if (balance == 0L) {
                    null
                } else {
                    AssetGroupShare(
                        groupType = groupType,
                        balance = balance,
                        accountCount = accounts.count { (statsData.currentBalances[it.id] ?: 0L) != 0L },
                    )
                }
            }
            .sortedByDescending { abs(it.balance) }
    }

    private fun buildTopAccountShares(
        statsData: StatsData,
    ): List<AccountShare> {
        return statsData.accounts.mapNotNull { account ->
            val balance = statsData.currentBalances[account.id] ?: 0L
            if (balance == 0L) {
                null
            } else {
                AccountShare(
                    accountName = account.name,
                    groupType = AccountGroupType.fromValue(account.groupType),
                    balance = balance,
                )
            }
        }.sortedByDescending { abs(it.balance) }
    }

    private fun buildCashFlowEvents(
        allCashFlows: List<CashFlowRecordEntity>,
    ): List<CashFlowEvent> {
        return allCashFlows
            .sortedBy { it.occurredAt }
            .map { record ->
                if (record.direction == "inflow") {
                    CashFlowEvent(
                        occurredAt = record.occurredAt,
                        inflow = record.amount,
                        outflow = 0L,
                    )
                } else {
                    CashFlowEvent(
                        occurredAt = record.occurredAt,
                        inflow = 0L,
                        outflow = record.amount,
                    )
                }
            }
    }

    private fun buildInvestmentOverview(
        statsData: StatsData,
    ): InvestmentOverview {
        val settlements = statsData.allInvestmentSettlements
            .filter { settlement ->
                statsData.accounts.any { account ->
                    account.id == settlement.accountId &&
                        AccountGroupType.fromValue(account.groupType) == AccountGroupType.INVESTMENT
                }
            }
        val denominator = settlements.sumOf { settlement ->
            max(settlement.previousBalance + settlement.netTransferIn - settlement.netTransferOut, 1L)
        }
        return InvestmentOverview(
            totalPnl = settlements.sumOf { it.pnl },
            weightedReturnRate = if (settlements.isEmpty()) null else {
                settlements.sumOf { it.pnl }.toDouble() / denominator.toDouble()
            },
            netTransferIn = settlements.sumOf { it.netTransferIn },
            netTransferOut = settlements.sumOf { it.netTransferOut },
            settlementCount = settlements.size,
        )
    }

    private fun buildBalanceEventsByAccount(
        statsData: StatsData,
    ): Map<Long, List<BalanceEvent>> {
        val eventsByAccount = mutableMapOf<Long, MutableList<BalanceEvent>>()

        fun addEvent(accountId: Long, event: BalanceEvent) {
            eventsByAccount.getOrPut(accountId) { mutableListOf() }.add(event)
        }

        statsData.accounts.forEach { account ->
            if (account.createdAt in statsData.overallRange.startAtMillis..statsData.overallRange.endAtMillis) {
                addEvent(
                    account.id,
                    BalanceEvent(
                        occurredAt = account.createdAt,
                        orderKey = -1_000_000L,
                        effect = BalanceEffect.Add(account.initialBalance),
                    ),
                )
            }
        }

        statsData.cashFlowsInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = if (record.direction == "inflow") {
                        BalanceEffect.Add(record.amount)
                    } else {
                        BalanceEffect.Add(-record.amount)
                    },
                ),
            )
        }
        statsData.transfersInRange.forEach { record ->
            addEvent(
                record.fromAccountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(-record.amount),
                ),
            )
            addEvent(
                record.toAccountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(record.amount),
                ),
            )
        }
        statsData.manualAdjustmentsInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 0L,
                    effect = BalanceEffect.Add(record.delta),
                ),
            )
        }
        statsData.balanceUpdatesInRange.forEach { record ->
            addEvent(
                record.accountId,
                BalanceEvent(
                    occurredAt = record.occurredAt,
                    orderKey = 1_000_000L + record.id,
                    effect = BalanceEffect.Reset(record.actualBalance),
                ),
            )
        }

        return eventsByAccount.mapValues { (_, events) ->
            events.sortedWith(compareBy<BalanceEvent> { it.occurredAt }.thenBy { it.orderKey })
        }
    }

    private fun resolveSubPeriodIndex(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
        occurredAt: Long,
    ): Int {
        return subPeriods.indexOfFirst { occurredAt in it.range.startAtMillis..it.range.endAtMillis }
    }
}

private data class StatsData(
    val overallRange: TimeRange,
    val accounts: List<AccountEntity>,
    val cashFlowsInRange: List<CashFlowRecordEntity>,
    val allCashFlows: List<CashFlowRecordEntity>,
    val transfersInRange: List<TransferRecordEntity>,
    val manualAdjustmentsInRange: List<BalanceAdjustmentRecordEntity>,
    val balanceUpdatesInRange: List<BalanceUpdateRecordEntity>,
    val allInvestmentSettlements: List<InvestmentSettlementEntity>,
    val currentBalances: Map<Long, Long>,
    val rangeStartBalances: Map<Long, Long>,
)

private data class BalanceEvent(
    val occurredAt: Long,
    val orderKey: Long,
    val effect: BalanceEffect,
) {
    fun applyTo(balance: Long): Long = when (effect) {
        is BalanceEffect.Add -> balance + effect.delta
        is BalanceEffect.Reset -> effect.value
    }
}

private sealed interface BalanceEffect {
    data class Add(val delta: Long) : BalanceEffect
    data class Reset(val value: Long) : BalanceEffect
}
