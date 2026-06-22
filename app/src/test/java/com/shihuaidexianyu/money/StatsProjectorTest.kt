package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.PurposeTotal
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

    @Test
    fun `buildDailyPoints fills missing days with zeros and preserves order`() {
        // Range = 2024-01-01 to 2024-01-03 (3 days).
        val startMillis = LocalDate.of(2024, 1, 1).atStartOfDay(utc).toInstant().toEpochMilli()
        val endMillisExclusive = LocalDate.of(2024, 1, 4).atStartOfDay(utc).toInstant().toEpochMilli()
        val range = TimeRange(startMillis, endMillisExclusive - 1)
        val dailyTotals = listOf(
            CashFlowDailyTotal(epochDay = LocalDate.of(2024, 1, 1).toEpochDay(), direction = CashFlowDirection.INFLOW.value, amount = 500),
            CashFlowDailyTotal(epochDay = LocalDate.of(2024, 1, 1).toEpochDay(), direction = CashFlowDirection.OUTFLOW.value, amount = 100),
            // 2024-01-02 has no data → should be (0, 0).
            CashFlowDailyTotal(epochDay = LocalDate.of(2024, 1, 3).toEpochDay(), direction = CashFlowDirection.INFLOW.value, amount = 200),
        )

        val points = StatsProjector.buildDailyPoints(dailyTotals, range, utc)

        assertEquals(3, points.size)
        assertEquals(LocalDate.of(2024, 1, 1), points[0].date)
        assertEquals(500L, points[0].inflow)
        assertEquals(100L, points[0].outflow)
        assertEquals(LocalDate.of(2024, 1, 2), points[1].date)
        assertEquals(0L, points[1].inflow)
        assertEquals(0L, points[1].outflow)
        assertEquals(LocalDate.of(2024, 1, 3), points[2].date)
        assertEquals(200L, points[2].inflow)
        assertEquals(0L, points[2].outflow)
    }

    @Test
    fun `project sorts account balances by descending balance and computes net cash flow`() {
        val accounts = listOf(
            Account(id = 1, name = "small", initialBalance = 100, createdAt = 1),
            Account(id = 2, name = "big", initialBalance = 9_000, createdAt = 2),
            Account(id = 3, name = "mid", initialBalance = 1_500, createdAt = 3),
        )
        val range = TimeRange(0L, 1_000L)
        val selection = StatsRangeSelection(period = StatsPeriod.MONTH, anchorMillis = 500L)
        val snapshot = StatsProjector.project(
            accounts = accounts,
            settings = AppSettings(),
            selection = selection,
            range = range,
            balances = mapOf(1L to 100L, 2L to 9_000L, 3L to 1_500L),
            openingBalanceByAccount = mapOf(1L to 100L, 2L to 9_000L, 3L to 1_500L),
            newAccountOpeningAssets = 0L,
            inflowTotals = listOf(PurposeTotal("工资", 1_000)),
            outflowTotals = listOf(PurposeTotal("吃饭", 300), PurposeTotal("打车", 50)),
            dailyTotals = emptyList(),
            manualAdjustmentIncrease = 0L,
            manualAdjustmentDecrease = 0L,
            reconciliationIncrease = 0L,
            reconciliationDecrease = 0L,
            zoneId = utc,
        )

        assertEquals(listOf(2L, 3L, 1L), snapshot.accountBalances.map { it.accountId })
        assertEquals(1_000L, snapshot.totalInflow)
        assertEquals(350L, snapshot.totalOutflow)
        assertEquals(650L, snapshot.netCashFlow)
        assertEquals(650L, snapshot.breakdown.cashNet)
        assertEquals(StatsPeriod.MONTH, snapshot.period)
        assertEquals(range, snapshot.range)
    }

    @Test
    fun `project adds newAccountOpeningAssets to openingAssets`() {
        val newAccount = Account(id = 9, name = "new", initialBalance = 5_000, createdAt = 100)
        val range = TimeRange(0L, 1_000L)
        val snapshot = StatsProjector.project(
            accounts = listOf(newAccount),
            settings = AppSettings(),
            selection = StatsRangeSelection(StatsPeriod.MONTH, 500L),
            range = range,
            balances = mapOf(9L to 5_000L),
            openingBalanceByAccount = emptyMap(),
            newAccountOpeningAssets = 5_000L,
            inflowTotals = emptyList(),
            outflowTotals = emptyList(),
            dailyTotals = emptyList(),
            manualAdjustmentIncrease = 0L,
            manualAdjustmentDecrease = 0L,
            reconciliationIncrease = 0L,
            reconciliationDecrease = 0L,
            zoneId = utc,
        )
        assertEquals(5_000L, snapshot.openingAssets)
        assertEquals(5_000L, snapshot.closingAssets)
        assertEquals(0L, snapshot.assetChange)
    }
}
