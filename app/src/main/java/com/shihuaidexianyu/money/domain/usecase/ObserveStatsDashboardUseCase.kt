package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.StatsPeriod
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
    val totalInflow: Long,
    val totalOutflow: Long,
    val netCashFlow: Long,
    val assetChange: Long,
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
    operator fun invoke(periodFlow: Flow<StatsPeriod>): Flow<StatsDashboardSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
            periodFlow,
        ) { accounts, settings, _, period ->
            StatsSource(accounts, settings, period)
        }.mapLatest { source ->
            buildSnapshot(source.accounts, source.settings, source.period)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        accounts: List<AccountEntity>,
        settings: AppSettings,
        period: StatsPeriod,
    ): StatsDashboardSnapshot = coroutineScope {
        val zoneId = ZoneId.systemDefault()
        val range = TimeRangeUtils.currentStatsRange(period, zoneId)
        val startBaselineAt = (range.startAtMillis - 1L).coerceAtLeast(0L)
        val inflowJob = async {
            transactionRepository.queryActiveCashFlowRecordsByDirectionBetween(
                direction = CashFlowDirection.INFLOW.value,
                startAt = range.startAtMillis,
                endAt = range.endAtMillis,
            )
        }
        val outflowJob = async {
            transactionRepository.queryActiveCashFlowRecordsByDirectionBetween(
                direction = CashFlowDirection.OUTFLOW.value,
                startAt = range.startAtMillis,
                endAt = range.endAtMillis,
            )
        }
        val balanceJobs = accounts.map { account ->
            async {
                account to calculateCurrentBalanceUseCase(account.id)
            }
        }
        val openingBalanceJobs = accounts
            .filter { it.createdAt <= startBaselineAt }
            .map { account ->
                async { calculateCurrentBalanceUseCase(account.id, startBaselineAt) }
            }

        val inflowRecords = inflowJob.await()
        val outflowRecords = outflowJob.await()
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
        val totalInflow = inflowRecords.sumOf { it.amount }
        val totalOutflow = outflowRecords.sumOf { it.amount }

        StatsDashboardSnapshot(
            settings = settings,
            period = period,
            range = range,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = totalInflow - totalOutflow,
            assetChange = currentAssets - openingAssets,
            purposeBreakdown = buildPurposeBreakdown(outflowRecords),
            dailyPoints = buildDailyPoints(inflowRecords, outflowRecords, range, zoneId),
            accountBalances = accountBalances,
        )
    }

    private fun buildPurposeBreakdown(records: List<CashFlowRecordEntity>): List<StatsPurposeBreakdown> {
        return records
            .groupBy { it.purpose.ifBlank { "未填写用途" } }
            .map { (purpose, recordsInGroup) ->
                StatsPurposeBreakdown(
                    purpose = purpose,
                    amount = recordsInGroup.sumOf { it.amount },
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun buildDailyPoints(
        inflowRecords: List<CashFlowRecordEntity>,
        outflowRecords: List<CashFlowRecordEntity>,
        range: TimeRange,
        zoneId: ZoneId,
    ): List<StatsDailyPoint> {
        val startDate = Instant.ofEpochMilli(range.startAtMillis).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(range.endAtMillis).atZone(zoneId).toLocalDate()
        val inflowByDate = inflowRecords.groupByDate(zoneId)
        val outflowByDate = outflowRecords.groupByDate(zoneId)
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

    private fun List<CashFlowRecordEntity>.groupByDate(zoneId: ZoneId): Map<LocalDate, Long> {
        return groupBy { record ->
            Instant.ofEpochMilli(record.occurredAt).atZone(zoneId).toLocalDate()
        }.mapValues { (_, records) -> records.sumOf { it.amount } }
    }
}

private data class StatsSource(
    val accounts: List<AccountEntity>,
    val settings: AppSettings,
    val period: StatsPeriod,
)
