package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowAnalysisEntry
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.usecase.StatsProjector
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class StatsProjectorTest {
    private val utc = ZoneId.of("UTC")
    private val range = TimeRange(
        LocalDate.of(2024, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli(),
        LocalDate.of(2024, 2, 1).atStartOfDay(utc).toInstant().toEpochMilli(),
    )

    @Test
    fun `single pass projection fills missing days and computes monthly net`() {
        val snapshot = project(
            listOf(
                entry(1L, CashFlowDirection.INFLOW, 500L, LocalDate.of(2024, 1, 1)),
                entry(1L, CashFlowDirection.OUTFLOW, 100L, LocalDate.of(2024, 1, 1)),
                entry(1L, CashFlowDirection.INFLOW, 200L, LocalDate.of(2024, 1, 3)),
            ),
        )

        assertEquals(31, snapshot.dailyPoints.size)
        assertEquals(500L, snapshot.dailyPoints[0].inflow)
        assertEquals(100L, snapshot.dailyPoints[0].outflow)
        assertEquals(0L, snapshot.dailyPoints[1].netFlow)
        assertEquals(200L, snapshot.dailyPoints[2].netFlow)
        assertEquals(700L, snapshot.totalInflow)
        assertEquals(100L, snapshot.totalOutflow)
        assertEquals(600L, snapshot.netCashFlow)
    }

    @Test
    fun `account cash flow ordering never adds Long values in Long`() {
        val accounts = listOf(
            Account(id = 1L, name = "极值账户", initialBalance = 0L, createdAt = 1L),
            Account(id = 2L, name = "普通账户", initialBalance = 0L, createdAt = 1L),
        )
        val snapshot = StatsProjector.project(
            accounts = accounts,
            settings = PortableSettings(),
            selection = StatsRangeSelection(StatsPeriod.MONTH, range.startInclusive),
            range = range,
            cashEntries = listOf(
                entry(1L, CashFlowDirection.INFLOW, Long.MAX_VALUE, LocalDate.of(2024, 1, 1)),
                entry(1L, CashFlowDirection.OUTFLOW, Long.MAX_VALUE, LocalDate.of(2024, 1, 2)),
                entry(2L, CashFlowDirection.INFLOW, 0L, LocalDate.of(2024, 1, 3)),
            ),
            transferPathTotals = emptyList(),
            zoneId = utc,
        )

        assertEquals(listOf(1L, 2L), snapshot.accountCashFlows.map { it.accountId })
        assertEquals(Long.MAX_VALUE, snapshot.accountCashFlows.first().inflow)
        assertEquals(Long.MAX_VALUE, snapshot.accountCashFlows.first().outflow)
    }

    private fun project(entries: List<CashFlowAnalysisEntry>) = StatsProjector.project(
        accounts = listOf(Account(id = 1L, name = "账户", initialBalance = 0L, createdAt = 1L)),
        settings = PortableSettings(),
        selection = StatsRangeSelection(StatsPeriod.MONTH, range.startInclusive),
        range = range,
        cashEntries = entries,
        transferPathTotals = emptyList(),
        zoneId = utc,
    )

    private fun entry(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        date: LocalDate,
    ) = CashFlowAnalysisEntry(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        occurredAt = date.atStartOfDay(utc).toInstant().toEpochMilli(),
    )
}
