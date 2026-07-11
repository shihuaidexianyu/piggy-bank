package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.usecase.calculateMonthlyBudgetStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test

class MonthlyBudgetPolicyTest {
    @Test
    fun `null disables budget and configured budget must be positive`() {
        assertNull(calculateMonthlyBudgetStatus(targetAmount = null, spentAmount = 0L))
        assertFailsWith<IllegalArgumentException> {
            calculateMonthlyBudgetStatus(targetAmount = 0L, spentAmount = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateMonthlyBudgetStatus(targetAmount = -1L, spentAmount = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateMonthlyBudgetStatus(targetAmount = 100L, spentAmount = -1L)
        }
    }

    @Test
    fun `geometry caps while percentage and exact overspend remain uncapped`() {
        val zero = requireNotNull(calculateMonthlyBudgetStatus(10_000L, 0L))
        assertEquals(0f, zero.progressFraction)
        assertEquals("0%", zero.percentageText)
        assertNull(zero.overBudgetAmount)

        val normal = requireNotNull(calculateMonthlyBudgetStatus(10_000L, 2_500L))
        assertEquals(0.25f, normal.progressFraction)
        assertEquals("25%", normal.percentageText)
        assertNull(normal.overBudgetAmount)

        val exact = requireNotNull(calculateMonthlyBudgetStatus(10_000L, 10_000L))
        assertEquals(1f, exact.progressFraction)
        assertEquals("100%", exact.percentageText)
        assertNull(exact.overBudgetAmount)

        val over = requireNotNull(calculateMonthlyBudgetStatus(10_000L, 15_001L))
        assertEquals(1f, over.progressFraction)
        assertEquals("150.01%", over.percentageText)
        assertEquals(5_001L, over.overBudgetAmount)
        assertEquals("50.01%", over.overBudgetPercentageText)
    }

    @Test
    fun `large Long values do not overflow or produce invalid geometry`() {
        val status = requireNotNull(
            calculateMonthlyBudgetStatus(
                targetAmount = Long.MAX_VALUE - 1L,
                spentAmount = Long.MAX_VALUE,
            ),
        )

        assertEquals(1f, status.progressFraction)
        assertEquals(1L, status.overBudgetAmount)
        assertEquals("100%", status.percentageText)
        assertEquals("<0.01%", status.overBudgetPercentageText)
    }
}
