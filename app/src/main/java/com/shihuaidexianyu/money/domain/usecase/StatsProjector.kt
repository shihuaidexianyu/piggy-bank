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
import com.shihuaidexianyu.money.domain.model.TransferPathTotal
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import java.math.BigInteger
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
        transferPathTotals: List<TransferPathTotal>,
        zoneId: ZoneId,
    ): StatsDashboardSnapshot {
        var totalInflow = 0L
        var totalOutflow = 0L
        val daily = mutableMapOf<LocalDate, SignedTotals>()
        val byAccount = mutableMapOf<Long, SignedTotals>()
        cashEntries.forEach { entry ->
            val date = Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId).toLocalDate()
            val dayTotals = daily.getOrPut(date, ::SignedTotals)
            val accountTotals = byAccount.getOrPut(entry.accountId, ::SignedTotals)
            when (entry.direction) {
                CashFlowDirection.INFLOW.value -> {
                    totalInflow = ledgerAddExact(totalInflow, entry.amount)
                    dayTotals.inflow = ledgerAddExact(dayTotals.inflow, entry.amount)
                    accountTotals.inflow = ledgerAddExact(accountTotals.inflow, entry.amount)
                }
                CashFlowDirection.OUTFLOW.value -> {
                    totalOutflow = ledgerAddExact(totalOutflow, entry.amount)
                    dayTotals.outflow = ledgerAddExact(dayTotals.outflow, entry.amount)
                    accountTotals.outflow = ledgerAddExact(accountTotals.outflow, entry.amount)
                }
            }
        }
        val names = accounts.associate { it.id to it.name }
        val accountCashFlows = byAccount.map { (accountId, totals) ->
            StatsAccountCashFlow(
                accountId = accountId,
                name = names[accountId] ?: "未知账户",
                inflow = totals.inflow,
                outflow = totals.outflow,
                inflowHistoryFilters = cashFilters(range, HistoryAmountDirection.INCREASE, accountId),
                outflowHistoryFilters = cashFilters(range, HistoryAmountDirection.DECREASE, accountId),
            )
        }.sortedWith { left, right ->
            flowMagnitude(right).compareTo(flowMagnitude(left))
        }
        val transferPaths = transferPathTotals.map { total ->
            StatsTransferPath(
                fromAccountId = total.fromAccountId,
                fromAccountName = names[total.fromAccountId] ?: "未知账户",
                toAccountId = total.toAccountId,
                toAccountName = names[total.toAccountId] ?: "未知账户",
                amount = total.amount,
                historyFilters = HistoryRecordFilters(
                    recordTypes = setOf(HistoryRecordType.TRANSFER),
                    transferFromAccountId = total.fromAccountId,
                    transferToAccountId = total.toAccountId,
                    dateStartAt = range.startInclusive,
                    dateEndAt = range.endExclusive,
                ),
            )
        }
        return StatsDashboardSnapshot(
            settings = settings,
            period = selection.period,
            range = range,
            zoneId = zoneId,
            hasSourceAccounts = accounts.isNotEmpty(),
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = ledgerSubtractExact(totalInflow, totalOutflow),
            dailyPoints = buildDailyPoints(daily, range, zoneId),
            accountCashFlows = accountCashFlows,
            transferPaths = transferPaths,
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

    private fun flowMagnitude(flow: StatsAccountCashFlow): BigInteger =
        BigInteger.valueOf(flow.inflow).add(BigInteger.valueOf(flow.outflow))

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
