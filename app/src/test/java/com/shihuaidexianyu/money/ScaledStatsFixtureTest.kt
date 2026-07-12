package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowAnalysisEntry
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.usecase.StatsProjector
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class ScaledStatsFixtureTest {
    @Test
    fun `one hundred thousand analysis rows retain exact totals and day assignment`() {
        val zone = ZoneId.of("Asia/Kathmandu")
        val anchor = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val range = TimeRangeCalculator.statsRange(StatsPeriod.MONTH, zone, anchor)
        val dayMillis = 24L * 60L * 60L * 1_000L
        val entries = List(100_000) { index ->
            CashFlowAnalysisEntry(
                accountId = 1L,
                direction = if (index % 2 == 0) {
                    CashFlowDirection.INFLOW.value
                } else {
                    CashFlowDirection.OUTFLOW.value
                },
                amount = 1L,
                occurredAt = range.startInclusive + (index % 31) * dayMillis + 1L,
            )
        }

        val snapshot = StatsProjector.project(
            accounts = listOf(Account(id = 1L, name = "规模账户", initialBalance = 0L, createdAt = 1L)),
            settings = PortableSettings(),
            selection = StatsRangeSelection(StatsPeriod.MONTH, anchor),
            range = range,
            cashEntries = entries,
            transferPathTotals = emptyList(),
            zoneId = zone,
        )

        assertEquals(50_000L, snapshot.totalInflow)
        assertEquals(50_000L, snapshot.totalOutflow)
        assertEquals(0L, snapshot.netCashFlow)
        assertEquals(100_000L, snapshot.dailyPoints.sumOf { it.inflow + it.outflow })
    }
}
