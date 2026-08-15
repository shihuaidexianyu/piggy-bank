package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
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
    fun `date picker conversion preserves local date east of UTC near midnight`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val localTime = LocalDateTime.of(2026, 8, 1, 1, 30)
            .atZone(shanghai)
            .toInstant()
            .toEpochMilli()

        val pickerMillis = DateTimeTextFormatter.toDatePickerMillis(localTime, shanghai)
        assertEquals("2026-08-01 00:00", DateTimeTextFormatter.format(pickerMillis, utc))

        val restored = DateTimeTextFormatter.fromDatePickerMillis(pickerMillis, shanghai)
        assertEquals("2026-08-01 00:00", DateTimeTextFormatter.format(restored, shanghai))
    }

    @Test
    fun `date picker conversion preserves local date west of UTC`() {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val localTime = LocalDateTime.of(2026, 8, 1, 23, 30)
            .atZone(losAngeles)
            .toInstant()
            .toEpochMilli()

        val pickerMillis = DateTimeTextFormatter.toDatePickerMillis(localTime, losAngeles)
        assertEquals("2026-08-01 00:00", DateTimeTextFormatter.format(pickerMillis, utc))

        val restored = DateTimeTextFormatter.fromDatePickerMillis(pickerMillis, losAngeles)
        assertEquals("2026-08-01 00:00", DateTimeTextFormatter.format(restored, losAngeles))
    }

    @Test
    fun `relative day time uses today and tomorrow labels`() {
        val now = LocalDateTime.of(2026, 8, 15, 12, 0).atZone(utc).toInstant().toEpochMilli()
        val todayMorning = LocalDateTime.of(2026, 8, 15, 9, 30).atZone(utc).toInstant().toEpochMilli()
        val tomorrowMorning = LocalDateTime.of(2026, 8, 16, 9, 30).atZone(utc).toInstant().toEpochMilli()

        assertEquals(
            "今天 09:30",
            DateTimeTextFormatter.formatRelativeDayTime(todayMorning, now, "今天", "明天", utc),
        )
        assertEquals(
            "明天 09:30",
            DateTimeTextFormatter.formatRelativeDayTime(tomorrowMorning, now, "今天", "明天", utc),
        )
    }

    @Test
    fun `relative day time falls back to month-day for other dates`() {
        val now = LocalDateTime.of(2026, 8, 15, 12, 0).atZone(utc).toInstant().toEpochMilli()
        val past = LocalDateTime.of(2026, 8, 10, 18, 5).atZone(utc).toInstant().toEpochMilli()
        val later = LocalDateTime.of(2026, 9, 1, 8, 0).atZone(utc).toInstant().toEpochMilli()

        assertEquals(
            "8月10日 18:05",
            DateTimeTextFormatter.formatRelativeDayTime(past, now, "今天", "明天", utc),
        )
        assertEquals(
            "9月1日 08:00",
            DateTimeTextFormatter.formatRelativeDayTime(later, now, "今天", "明天", utc),
        )
    }

    @Test
    fun `day in year omits the year inside the current year only`() {
        val now = LocalDateTime.of(2026, 8, 15, 12, 0).atZone(utc).toInstant().toEpochMilli()
        val sameYear = LocalDateTime.of(2026, 2, 3, 0, 0).atZone(utc).toInstant().toEpochMilli()
        val lastYear = LocalDateTime.of(2025, 12, 31, 23, 0).atZone(utc).toInstant().toEpochMilli()

        assertEquals("2月3日", DateTimeTextFormatter.formatDayInYear(sameYear, now, utc))
        assertEquals("2025年12月31日", DateTimeTextFormatter.formatDayInYear(lastYear, now, utc))
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
