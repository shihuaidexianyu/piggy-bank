package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.usecase.calculatePeriodDelta
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PeriodDeltaPolicyTest {
    @Test
    fun `increase reports a positive delta and percentage`() {
        val delta = calculatePeriodDelta(currentAmount = 128_400_00L, baselineAmount = 125_200_00L)

        assertEquals(3_200_00L, delta.deltaAmount)
        assertTrue(delta.isIncrease)
        assertFalse(delta.isDecrease)
        assertEquals("2.6%", delta.percentageText)
    }

    @Test
    fun `decrease keeps the percentage unsigned so the arrow carries the direction`() {
        val delta = calculatePeriodDelta(currentAmount = 90_000L, baselineAmount = 100_000L)

        assertEquals(-10_000L, delta.deltaAmount)
        assertTrue(delta.isDecrease)
        assertEquals("10%", delta.percentageText)
    }

    @Test
    fun `no percentage against a zero baseline`() {
        val delta = calculatePeriodDelta(currentAmount = 5_000L, baselineAmount = 0L)

        assertEquals(5_000L, delta.deltaAmount)
        assertNull(delta.percentageText)
    }

    @Test
    fun `no percentage against a negative baseline`() {
        // Net worth climbing from -100.00 to -50.00 is an improvement; "-50%" would read as a loss.
        val delta = calculatePeriodDelta(currentAmount = -5_000L, baselineAmount = -10_000L)

        assertEquals(5_000L, delta.deltaAmount)
        assertTrue(delta.isIncrease)
        assertNull(delta.percentageText)
    }

    @Test
    fun `unchanged reports zero percent`() {
        val delta = calculatePeriodDelta(currentAmount = 100_000L, baselineAmount = 100_000L)

        assertTrue(delta.isUnchanged)
        assertEquals("0%", delta.percentageText)
    }

    @Test
    fun `a change too small to round is not reported as zero`() {
        val delta = calculatePeriodDelta(currentAmount = 10_000_001L, baselineAmount = 10_000_000L)

        assertEquals(1L, delta.deltaAmount)
        assertEquals("<0.1%", delta.percentageText)
    }
}
