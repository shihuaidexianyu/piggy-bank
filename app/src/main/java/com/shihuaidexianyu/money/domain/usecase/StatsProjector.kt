package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowAnalysisEntry
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One-pass natural-month projection. Notes never enter the analysis input. */
internal object StatsProjector {
    fun project(
        accounts: List<Account>,
        settings: PortableSettings,
        selection: StatsRangeSelection,
        range: TimeRange,
        cashEntries: List<CashFlowAnalysisEntry>,
        zoneId: ZoneId,
        openingAssets: Long = 0L,
        closingAssets: Long = 0L,
    ): StatsDashboardSnapshot {
        var totalInflow = 0L
        var totalOutflow = 0L
        val daily = mutableMapOf<LocalDate, SignedTotals>()
        cashEntries.forEach { entry ->
            val date = Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId).toLocalDate()
            val dayTotals = daily.getOrPut(date, ::SignedTotals)
            when (entry.direction) {
                CashFlowDirection.INFLOW.value -> {
                    totalInflow = ledgerAddExact(totalInflow, entry.amount)
                    dayTotals.inflow = ledgerAddExact(dayTotals.inflow, entry.amount)
                }
                CashFlowDirection.OUTFLOW.value -> {
                    totalOutflow = ledgerAddExact(totalOutflow, entry.amount)
                    dayTotals.outflow = ledgerAddExact(dayTotals.outflow, entry.amount)
                }
            }
        }
        val netCashFlow = ledgerSubtractExact(totalInflow, totalOutflow)
        val assetAdjustment = ledgerSubtractExact(
            ledgerSubtractExact(closingAssets, openingAssets),
            netCashFlow,
        )
        return StatsDashboardSnapshot(
            settings = settings,
            period = selection.period,
            range = range,
            zoneId = zoneId,
            hasSourceAccounts = accounts.isNotEmpty(),
            openingAssets = openingAssets,
            closingAssets = closingAssets,
            assetAdjustment = assetAdjustment,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            dailyPoints = buildDailyPoints(daily, range, zoneId),
            accountCashFlows = emptyList(),
            transferPaths = emptyList(),
            inflowHistoryFilters = cashFilters(range, HistoryAmountDirection.INCREASE),
            outflowHistoryFilters = cashFilters(range, HistoryAmountDirection.DECREASE),
            netCashFlowHistoryFilters = cashFilters(range, HistoryAmountDirection.ALL),
        )
    }

    private fun buildDailyPoints(
        totalsByDate: Map<LocalDate, SignedTotals>,
        range: TimeRange,
        zoneId: ZoneId,
    ): List<StatsDailyPoint> {
        val startDate = Instant.ofEpochMilli(range.startInclusive).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(range.endExclusive).atZone(zoneId).toLocalDate().minusDays(1)
        return generateSequence(startDate) { date -> date.plusDays(1).takeIf { !it.isAfter(endDate) } }
            .map { date ->
                val totals = totalsByDate[date] ?: SignedTotals()
                val dayRange = TimeRange(
                    date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                )
                StatsDailyPoint(
                    date = date,
                    inflow = totals.inflow,
                    outflow = totals.outflow,
                    netFlow = ledgerSubtractExact(totals.inflow, totals.outflow),
                    historyFilters = cashFilters(dayRange, HistoryAmountDirection.ALL),
                    inflowHistoryFilters = cashFilters(dayRange, HistoryAmountDirection.INCREASE),
                    outflowHistoryFilters = cashFilters(dayRange, HistoryAmountDirection.DECREASE),
                )
            }.toList()
    }

    private fun cashFilters(
        range: TimeRange,
        direction: HistoryAmountDirection,
        accountId: Long? = null,
    ): HistoryRecordFilters = HistoryRecordFilters(
        recordTypes = setOf(HistoryRecordType.CASH_FLOW),
        accountId = accountId,
        dateStartAt = range.startInclusive,
        dateEndAt = range.endExclusive,
        amountDirection = direction,
    )

    private data class SignedTotals(var inflow: Long = 0L, var outflow: Long = 0L)
}
