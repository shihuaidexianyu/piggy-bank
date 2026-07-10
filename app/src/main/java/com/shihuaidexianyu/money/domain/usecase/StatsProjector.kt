package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure projection from raw ledger inputs to a [StatsDashboardSnapshot]. Extracted from
 * [ObserveStatsDashboardUseCase] so the math (incl. [buildDailyPoints]) can be unit-tested without I/O.
 */
internal object StatsProjector {
    fun project(
        accounts: List<Account>,
        settings: AppSettings,
        selection: StatsRangeSelection,
        range: TimeRange,
        balances: Map<Long, Long>,
        openingBalanceByAccount: Map<Long, Long>,
        newAccountOpeningAssets: Long,
        inflowTotals: List<com.shihuaidexianyu.money.domain.model.PurposeTotal>,
        outflowTotals: List<com.shihuaidexianyu.money.domain.model.PurposeTotal>,
        dailyTotals: List<CashFlowDailyTotal>,
        manualAdjustmentIncrease: Long,
        manualAdjustmentDecrease: Long,
        reconciliationIncrease: Long,
        reconciliationDecrease: Long,
        zoneId: ZoneId,
    ): StatsDashboardSnapshot {
        val totalInflow = inflowTotals.map { it.amount }.ledgerSumExact()
        val totalOutflow = outflowTotals.map { it.amount }.ledgerSumExact()
        val accountBalances = accounts.map { account ->
            StatsAccountBalance(
                accountId = account.id,
                name = account.name,
                colorName = account.colorName,
                balance = balances.getValue(account.id),
            )
        }.sortedByDescending { it.balance }
        val currentAssets = accountBalances.map { it.balance }.ledgerSumExact()
        val openingAssets = ledgerAddExact(
            openingBalanceByAccount.values.ledgerSumExact(),
            newAccountOpeningAssets,
        )
        val netCashFlow = ledgerSubtractExact(totalInflow, totalOutflow)
        val assetChange = ledgerSubtractExact(currentAssets, openingAssets)
        val breakdown = PeriodAssetBreakdown(
            openingAssets = openingAssets,
            closingAssets = currentAssets,
            assetChange = assetChange,
            cashInflow = totalInflow,
            cashOutflow = totalOutflow,
            manualAdjustmentIncrease = manualAdjustmentIncrease,
            manualAdjustmentDecrease = manualAdjustmentDecrease,
            reconciliationIncrease = reconciliationIncrease,
            reconciliationDecrease = reconciliationDecrease,
        )
        return StatsDashboardSnapshot(
            settings = settings,
            period = selection.period,
            range = range,
            openingAssets = openingAssets,
            closingAssets = currentAssets,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            assetChange = assetChange,
            assetAdjustment = ledgerAddExact(breakdown.manualAdjustmentNet, breakdown.reconciliationNet),
            manualAdjustmentNet = breakdown.manualAdjustmentNet,
            reconciliationNet = breakdown.reconciliationNet,
            breakdown = breakdown,
            purposeBreakdown = outflowTotals.map { StatsPurposeBreakdown(purpose = it.purpose, amount = it.amount) },
            dailyPoints = buildDailyPoints(dailyTotals, range, zoneId),
            accountBalances = accountBalances,
        )
    }

    fun buildDailyPoints(
        dailyTotals: List<CashFlowDailyTotal>,
        range: TimeRange,
        zoneId: ZoneId,
    ): List<StatsDailyPoint> {
        val startDate = Instant.ofEpochMilli(range.startInclusive).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(range.endExclusive).atZone(zoneId).toLocalDate().minusDays(1)
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
