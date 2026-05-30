package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeRangeUtilsTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun `week range starts on Monday`() {
        // 2024-04-03 is Wednesday
        val wednesday = 1712140200000L
        val range = TimeRangeUtils.currentWeekRange(utc, wednesday)
        // Monday 2024-04-01 00:00 UTC
        val mondayStart = Instant.parse("2024-04-01T00:00:00Z").toEpochMilli()
        assertEquals(mondayStart, range.startAtMillis)
    }

    @Test
    fun `week range ends on Sunday`() {
        // 2024-04-03 is Wednesday
        val wednesday = 1712140200000L
        val range = TimeRangeUtils.currentWeekRange(utc, wednesday)
        // Sunday 2024-04-07 23:59:59.999 UTC = Monday 2024-04-08 00:00:00 - 1
        val sundayEnd = Instant.parse("2024-04-08T00:00:00Z").toEpochMilli() - 1
        assertEquals(sundayEnd, range.endAtMillis)
    }

    @Test
    fun `month range covers full month`() {
        // 2024-02-15 (leap year)
        val feb15 = Instant.parse("2024-02-15T10:00:00Z").toEpochMilli()
        val range = TimeRangeUtils.currentMonthRange(utc, feb15)
        val feb1 = Instant.parse("2024-02-01T00:00:00Z").toEpochMilli()
        val mar1 = Instant.parse("2024-03-01T00:00:00Z").toEpochMilli()
        assertEquals(feb1, range.startAtMillis)
        assertEquals(mar1 - 1, range.endAtMillis)
    }

    @Test
    fun `currentRange dispatches to week`() {
        val now = 1712140200000L
        val weekRange = TimeRangeUtils.currentWeekRange(utc, now)
        val dispatched = TimeRangeUtils.currentRange(HomePeriod.WEEK, utc, now)
        assertEquals(weekRange, dispatched)
    }

    @Test
    fun `currentRange dispatches to month`() {
        val now = 1712140200000L
        val monthRange = TimeRangeUtils.currentMonthRange(utc, now)
        val dispatched = TimeRangeUtils.currentRange(HomePeriod.MONTH, utc, now)
        assertEquals(monthRange, dispatched)
    }

    @Test
    fun `statsRange uses anchor month instead of current month`() {
        val anchor = Instant.parse("2024-01-15T10:00:00Z").toEpochMilli()
        val range = TimeRangeUtils.statsRange(StatsPeriod.MONTH, utc, anchor)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(), range.startAtMillis)
        assertEquals(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli() - 1, range.endAtMillis)
    }

    @Test
    fun `range start is before end`() {
        val now = System.currentTimeMillis()
        val range = TimeRangeUtils.currentWeekRange(utc, now)
        assertTrue(range.startAtMillis < range.endAtMillis)
    }
}
