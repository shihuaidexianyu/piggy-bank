package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.TimeRange
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeRangeCalculatorTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun `current month range covers full month`() {
        // 2024-02-15 (leap year)
        val feb15 = Instant.parse("2024-02-15T10:00:00Z").toEpochMilli()
        val range = TimeRangeCalculator.currentMonthRange(utc, feb15)
        val feb1 = Instant.parse("2024-02-01T00:00:00Z").toEpochMilli()
        val mar1 = Instant.parse("2024-03-01T00:00:00Z").toEpochMilli()
        assertEquals(feb1, range.startInclusive)
        assertEquals(mar1, range.endExclusive)
        assertTrue(contains(range, Instant.parse("2024-02-29T12:00:00Z").toEpochMilli()))
    }

    @Test
    fun `exact start belongs to period and exact end belongs only to next period`() {
        val january = TimeRangeCalculator.currentMonthRange(
            utc,
            Instant.parse("2024-01-15T12:00:00Z").toEpochMilli(),
        )
        val february = TimeRangeCalculator.currentMonthRange(
            utc,
            Instant.parse("2024-02-15T12:00:00Z").toEpochMilli(),
        )

        assertTrue(contains(january, january.startInclusive))
        assertTrue(!contains(january, january.endExclusive))
        assertTrue(contains(february, january.endExclusive))
    }

    @Test
    fun `adjacent month ranges have no overlap or gap`() {
        val january = TimeRangeCalculator.currentMonthRange(
            utc,
            Instant.parse("2024-01-15T12:00:00Z").toEpochMilli(),
        )
        val february = TimeRangeCalculator.currentMonthRange(
            utc,
            Instant.parse("2024-02-15T12:00:00Z").toEpochMilli(),
        )

        assertEquals(january.endExclusive, february.startInclusive)
    }

    private fun contains(range: TimeRange, instant: Long): Boolean =
        instant >= range.startInclusive && instant < range.endExclusive
}
