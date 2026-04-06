package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest

data class CashFlowBar(
    val label: String,
    val inflow: Long,
    val outflow: Long,
)

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

data class InvestmentPoint(
    val label: String,
    val timestamp: Long,
    val pnl: Long,
    val returnRate: Double,
)

data class StatsSnapshot(
    val settings: AppSettings,
    val period: StatsPeriod,
    val cashFlowBars: List<CashFlowBar>,
    val netAssetPoints: List<NetAssetPoint>,
    val accountShares: List<AccountShare>,
    val investmentPoints: List<InvestmentPoint>,
)

class ObserveStatsUseCase(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
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
        val subPeriods = TimeRangeUtils.splitIntoPeriods(period)
        val overallRange = TimeRangeUtils.currentStatsRange(period)

        val cashFlowBarsJob = async { buildCashFlowBars(subPeriods) }
        val netAssetJob = async { buildNetAssetPoints(subPeriods) }
        val accountSharesJob = async { buildAccountShares() }
        val investmentJob = async { buildInvestmentPoints(overallRange) }

        StatsSnapshot(
            settings = settings,
            period = period,
            cashFlowBars = cashFlowBarsJob.await(),
            netAssetPoints = netAssetJob.await(),
            accountShares = accountSharesJob.await(),
            investmentPoints = investmentJob.await(),
        )
    }

    private suspend fun buildCashFlowBars(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
    ): List<CashFlowBar> {
        return subPeriods.map { sub ->
            val inflow = transactionRepository.sumAllInflowBetween(
                sub.range.startAtMillis, sub.range.endAtMillis,
            )
            val outflow = transactionRepository.sumAllOutflowBetween(
                sub.range.startAtMillis, sub.range.endAtMillis,
            )
            CashFlowBar(label = sub.label, inflow = inflow, outflow = outflow)
        }
    }

    private suspend fun buildNetAssetPoints(
        subPeriods: List<TimeRangeUtils.SubPeriod>,
    ): List<NetAssetPoint> {
        val accounts = accountRepository.queryActiveAccounts()
        if (accounts.isEmpty()) return emptyList()

        return subPeriods.map { sub ->
            var total = 0L
            for (account in accounts) {
                val latestUpdate = transactionRepository.getLatestBalanceUpdateAtOrBefore(
                    account.id, sub.range.endAtMillis,
                )
                if (latestUpdate != null) {
                    val baseBalance = latestUpdate.actualBalance
                    val inflowAfter = transactionRepository.sumInflowBetween(
                        account.id, latestUpdate.occurredAt, sub.range.endAtMillis,
                    )
                    val outflowAfter = transactionRepository.sumOutflowBetween(
                        account.id, latestUpdate.occurredAt, sub.range.endAtMillis,
                    )
                    val transferIn = transactionRepository.sumTransferInBetween(
                        account.id, latestUpdate.occurredAt, sub.range.endAtMillis,
                    )
                    val transferOut = transactionRepository.sumTransferOutBetween(
                        account.id, latestUpdate.occurredAt, sub.range.endAtMillis,
                    )
                    val adjustment = transactionRepository.sumAdjustmentBetween(
                        account.id, latestUpdate.occurredAt, sub.range.endAtMillis,
                    )
                    total += baseBalance + inflowAfter - outflowAfter + transferIn - transferOut + adjustment
                } else {
                    val inflowAfter = transactionRepository.sumInflowBetween(
                        account.id, 0L, sub.range.endAtMillis,
                    )
                    val outflowAfter = transactionRepository.sumOutflowBetween(
                        account.id, 0L, sub.range.endAtMillis,
                    )
                    val transferIn = transactionRepository.sumTransferInBetween(
                        account.id, 0L, sub.range.endAtMillis,
                    )
                    val transferOut = transactionRepository.sumTransferOutBetween(
                        account.id, 0L, sub.range.endAtMillis,
                    )
                    val adjustment = transactionRepository.sumAdjustmentBetween(
                        account.id, 0L, sub.range.endAtMillis,
                    )
                    total += account.initialBalance + inflowAfter - outflowAfter + transferIn - transferOut + adjustment
                }
            }
            NetAssetPoint(
                label = sub.label,
                timestamp = sub.range.endAtMillis,
                totalBalance = total,
            )
        }
    }

    private suspend fun buildAccountShares(): List<AccountShare> {
        val accounts = accountRepository.queryActiveAccounts()
        return accounts.map { account ->
            val balance = calculateCurrentBalanceUseCase(account.id)
            AccountShare(
                accountName = account.name,
                groupType = AccountGroupType.fromValue(account.groupType),
                balance = balance,
            )
        }.filter { it.balance != 0L }
    }

    private suspend fun buildInvestmentPoints(
        overallRange: com.shihuaidexianyu.money.util.TimeRange,
    ): List<InvestmentPoint> {
        val accounts = accountRepository.queryActiveAccounts()
            .filter { AccountGroupType.fromValue(it.groupType) == AccountGroupType.INVESTMENT }
        if (accounts.isEmpty()) return emptyList()

        val allSettlements = accounts.flatMap { account ->
            transactionRepository.queryInvestmentSettlementsByAccountId(account.id)
        }.filter { it.periodEndAt in overallRange.startAtMillis..overallRange.endAtMillis }
            .sortedBy { it.periodEndAt }

        return allSettlements.map { settlement ->
            InvestmentPoint(
                label = java.time.Instant.ofEpochMilli(settlement.periodEndAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .let { "${it.monthValue}/${it.dayOfMonth}" },
                timestamp = settlement.periodEndAt,
                pnl = settlement.pnl,
                returnRate = settlement.returnRate,
            )
        }
    }
}
