package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DateTimeTextFormatterTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun `format produces expected pattern`() {
        // 2024-04-03 10:30 UTC
        val millis = 1712140200000L
        assertEquals("2024-04-03 10:30", DateTimeTextFormatter.format(millis, utc))
    }

    @Test
    fun `floorToMinute truncates seconds and millis`() {
        // 60_000 * 5 + 30_999 = 330_999 (5 min 30.999s)
        assertEquals(300_000, DateTimeTextFormatter.floorToMinute(330_999))
    }

    @Test
    fun `floorToMinute on exact minute is unchanged`() {
        assertEquals(300_000, DateTimeTextFormatter.floorToMinute(300_000))
    }

    @Test
    fun `floorToMinute on zero is zero`() {
        assertEquals(0, DateTimeTextFormatter.floorToMinute(0))
    }

    @Test
    fun `formatDateOnly produces date without time`() {
        val millis = 1712140200000L
        assertEquals("2024-04-03", DateTimeTextFormatter.formatDateOnly(millis, utc))
    }

    @Test
    fun `formatTimeOnly produces time without date`() {
        val millis = 1712140200000L
        assertEquals("10:30", DateTimeTextFormatter.formatTimeOnly(millis, utc))
    }

    @Test
    fun `replaceDate keeps time, changes date`() {
        // base: 2024-04-03 10:30 UTC
        val base = 1712140200000L
        // selectedDate: 2024-05-01 00:00 UTC
        val selectedDate = 1714521600000L
        val result = DateTimeTextFormatter.replaceDate(base, selectedDate, utc)
        assertEquals("2024-05-01 10:30", DateTimeTextFormatter.format(result, utc))
    }

    @Test
    fun `replaceTime keeps date, changes time`() {
        // base: 2024-04-03 10:30 UTC
        val base = 1712140200000L
        val result = DateTimeTextFormatter.replaceTime(base, 14, 45, utc)
        assertEquals("2024-04-03 14:45", DateTimeTextFormatter.format(result, utc))
    }

    @Test
    fun `startOfDayMillis returns midnight`() {
        val millis = 1712140200000L // 2024-04-03 10:30 UTC
        val start = DateTimeTextFormatter.startOfDayMillis(millis, utc)
        assertEquals("2024-04-03 00:00", DateTimeTextFormatter.format(start, utc))
    }

    @Test
    fun `day end returns the exclusive next midnight`() {
        val millis = 1712140200000L // 2024-04-03 10:30 UTC
        val end = DateTimeTextFormatter.endExclusiveOfDayMillis(millis, utc)
        val start = DateTimeTextFormatter.startOfDayMillis(millis, utc)
        assertTrue(end > start)
        val nextDay = start + 86_400_000L
        assertEquals(nextDay, end)
    }
}
