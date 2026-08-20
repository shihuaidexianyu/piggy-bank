package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.DashboardPeriod
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

    @Test
    fun `week range starts on monday`() {
        // 2024-02-15 is a Thursday.
        val thursday = Instant.parse("2024-02-15T10:00:00Z").toEpochMilli()
        val range = TimeRangeCalculator.rangeFor(DashboardPeriod.WEEK, utc, thursday)

        assertEquals(Instant.parse("2024-02-12T00:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2024-02-19T00:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun `monday belongs to the week it starts`() {
        val monday = Instant.parse("2024-02-12T00:00:00Z").toEpochMilli()
        val range = TimeRangeCalculator.rangeFor(DashboardPeriod.WEEK, utc, monday)

        assertEquals(monday, range.startInclusive)
        assertTrue(contains(range, monday))
    }

    @Test
    fun `year range covers the calendar year`() {
        val midYear = Instant.parse("2024-07-04T12:00:00Z").toEpochMilli()
        val range = TimeRangeCalculator.rangeFor(DashboardPeriod.YEAR, utc, midYear)

        assertEquals(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test
    fun `month range matches the legacy helper`() {
        val now = Instant.parse("2024-02-15T10:00:00Z").toEpochMilli()

        assertEquals(
            TimeRangeCalculator.currentMonthRange(utc, now),
            TimeRangeCalculator.rangeFor(DashboardPeriod.MONTH, utc, now),
        )
    }

    private fun contains(range: TimeRange, instant: Long): Boolean =
        instant >= range.startInclusive && instant < range.endExclusive
}
