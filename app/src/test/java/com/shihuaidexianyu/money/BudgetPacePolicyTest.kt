package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.usecase.calculateBudgetPace
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BudgetPacePolicyTest {
    @Test
    fun `projects overspend when the pace exceeds the target`() {
        // Spent 2,040.00 over 12 days of a 30-day month against a 3,000.00 budget:
        // 170.00/day projects to 5,100.00, i.e. 2,100.00 over.
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 300_000L,
                spentAmount = 204_000L,
                daysElapsed = 12,
                daysTotal = 30,
            ),
        )

        assertEquals(17_000L, pace.dailyAverageSpent)
        assertEquals(510_000L, pace.projectedTotalSpend)
        assertEquals(210_000L, pace.projectedOverspend)
        assertEquals(18, pace.daysRemaining)
    }

    @Test
    fun `reports no overspend when the pace lands within the target`() {
        // 30.00/day over 30 days projects to 900.00 against a 1,000.00 budget.
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 30_000L,
                daysElapsed = 10,
                daysTotal = 30,
            ),
        )

        assertNull(pace.projectedOverspend)
        assertEquals(90_000L, pace.projectedTotalSpend)
        assertEquals(70_000L, pace.remainingBudget)
    }

    @Test
    fun `safe daily spend divides the remaining budget across the remaining days`() {
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 40_000L,
                daysElapsed = 10,
                daysTotal = 30,
            ),
        )

        // 600.00 left across 20 remaining days.
        assertEquals(3_000L, pace.safeDailySpend)
    }

    @Test
    fun `safe daily spend is absent once the budget is exhausted`() {
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 120_000L,
                daysElapsed = 20,
                daysTotal = 30,
            ),
        )

        assertNull(pace.safeDailySpend)
        assertEquals(-20_000L, pace.remainingBudget)
    }

    @Test
    fun `last day of the period leaves no remaining days`() {
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 90_000L,
                daysElapsed = 30,
                daysTotal = 30,
            ),
        )

        assertEquals(0, pace.daysRemaining)
        assertNull(pace.safeDailySpend)
        // A full period elapsed means the projection is simply what was actually spent.
        assertEquals(90_000L, pace.projectedTotalSpend)
    }

    @Test
    fun `elapsed days are clamped into the period`() {
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 50_000L,
                daysElapsed = 99,
                daysTotal = 30,
            ),
        )

        assertEquals(30, pace.daysElapsed)
        assertEquals(0, pace.daysRemaining)
    }

    @Test
    fun `no pace without a positive budget`() {
        assertNull(calculateBudgetPace(targetAmount = 0L, spentAmount = 100L, daysElapsed = 1, daysTotal = 30))
        assertNull(calculateBudgetPace(targetAmount = -1L, spentAmount = 100L, daysElapsed = 1, daysTotal = 30))
    }

    @Test
    fun `zero spend projects zero and keeps the whole budget available`() {
        val pace = requireNotNull(
            calculateBudgetPace(
                targetAmount = 100_000L,
                spentAmount = 0L,
                daysElapsed = 5,
                daysTotal = 30,
            ),
        )

        assertEquals(0L, pace.dailyAverageSpent)
        assertEquals(0L, pace.projectedTotalSpend)
        assertNull(pace.projectedOverspend)
        assertTrue(pace.safeDailySpend!! > 0L)
    }
}
