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

    @Test
    fun `previous range is adjacent to the current range for every period`() {
        val now = Instant.parse("2024-02-15T10:00:00Z").toEpochMilli()

        DashboardPeriod.entries.forEach { period ->
            val current = TimeRangeCalculator.rangeFor(period, utc, now)
            val previous = TimeRangeCalculator.previousRangeFor(period, utc, now)

            assertEquals(current.startInclusive, previous.endExclusive, "gap or overlap for $period")
            assertTrue(previous.startInclusive < previous.endExclusive, "empty previous range for $period")
        }
    }

    @Test
    fun `previous month handles the leap-year boundary`() {
        // Comparing March against February must not slide off the end of a 29-day month.
        val march = Instant.parse("2024-03-31T10:00:00Z").toEpochMilli()
        val previous = TimeRangeCalculator.previousRangeFor(DashboardPeriod.MONTH, utc, march)

        assertEquals(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli(), previous.startInclusive)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(), previous.endExclusive)
    }

    @Test
    fun `progress days count today as elapsed`() {
        // 2024-02-15 is the 15th day of a 29-day month.
        val feb15 = Instant.parse("2024-02-15T23:59:00Z").toEpochMilli()
        val progress = TimeRangeCalculator.periodProgressDays(DashboardPeriod.MONTH, utc, feb15)

        assertEquals(15, progress.elapsed)
        assertEquals(29, progress.total)
        assertEquals(14, progress.remaining)
    }

    @Test
    fun `progress days on the first day of a week`() {
        val monday = Instant.parse("2024-02-12T08:00:00Z").toEpochMilli()
        val progress = TimeRangeCalculator.periodProgressDays(DashboardPeriod.WEEK, utc, monday)

        assertEquals(1, progress.elapsed)
        assertEquals(7, progress.total)
        assertEquals(6, progress.remaining)
    }

    @Test
    fun `progress days on the last day of a year`() {
        val newYearsEve = Instant.parse("2024-12-31T23:00:00Z").toEpochMilli()
        val progress = TimeRangeCalculator.periodProgressDays(DashboardPeriod.YEAR, utc, newYearsEve)

        assertEquals(366, progress.elapsed)
        assertEquals(366, progress.total)
        assertEquals(0, progress.remaining)
    }

    private fun contains(range: TimeRange, instant: Long): Boolean =
        instant >= range.startInclusive && instant < range.endExclusive
}
