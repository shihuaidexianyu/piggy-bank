package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.normalizeHistoryDateRange
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

class HistoryDateRangeNormalizationTest {
    private val utc = ZoneOffset.UTC

    private fun dayStart(date: String): Long =
        LocalDate.parse(date).atStartOfDay(utc).toInstant().toEpochMilli()

    /** What the end-date picker produces: the first instant of the day AFTER the picked day. */
    private fun dayEndExclusive(date: String): Long =
        LocalDate.parse(date).plusDays(1).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun `valid range passes through untouched`() {
        val start = dayStart("2026-07-10")
        val end = dayEndExclusive("2026-07-20")

        assertEquals(start to end, normalizeHistoryDateRange(start, end, utc))
    }

    @Test
    fun `same-day range is valid and untouched`() {
        val start = dayStart("2026-07-10")
        val end = dayEndExclusive("2026-07-10")

        assertEquals(start to end, normalizeHistoryDateRange(start, end, utc))
    }

    @Test
    fun `inverted picks swap day-wise so both picked days stay inside the range`() {
        // User picked start = 07-20 and end = 07-10.
        val (start, end) = normalizeHistoryDateRange(
            dayStart("2026-07-20"),
            dayEndExclusive("2026-07-10"),
            utc,
        )

        // The swapped range must cover 07-10 through 07-20 inclusive.
        assertEquals(dayStart("2026-07-10"), start)
        assertEquals(dayEndExclusive("2026-07-20"), end)
    }

    @Test
    fun `start day directly after end day is caught even though the raw millis are equal`() {
        // start = 07-11 (day start) and end = 07-10 (exclusive end = 07-11T00:00): equal millis,
        // which the old `>` comparison let through as an empty, backwards-labeled range.
        val (start, end) = normalizeHistoryDateRange(
            dayStart("2026-07-11"),
            dayEndExclusive("2026-07-10"),
            utc,
        )

        assertEquals(dayStart("2026-07-10"), start)
        assertEquals(dayEndExclusive("2026-07-11"), end)
        // And the displayed labels read forwards.
        assertEquals(
            dayStart("2026-07-11"),
            DateTimeTextFormatter.startOfDisplayedEndDateMillis(requireNotNull(end), utc),
        )
    }

    @Test
    fun `open-ended ranges are never swapped`() {
        val start = dayStart("2026-07-20")
        val end = dayEndExclusive("2026-07-10")

        assertEquals(start to null, normalizeHistoryDateRange(start, null, utc))
        assertEquals(null to end, normalizeHistoryDateRange(null, end, utc))
        assertEquals(null to null, normalizeHistoryDateRange(null, null, utc))
    }

    @Test
    fun `swap keeps the half-open contract`() {
        val (start, end) = normalizeHistoryDateRange(
            dayStart("2026-07-20"),
            dayEndExclusive("2026-07-10"),
            utc,
        )
        val startMillis = requireNotNull(start)
        val endMillis = requireNotNull(end)

        // Both originally picked days fall inside [start, end).
        val pickedEndDay = Instant.ofEpochMilli(dayStart("2026-07-10")).toEpochMilli()
        val pickedStartDay = Instant.ofEpochMilli(dayStart("2026-07-20")).toEpochMilli()
        assert(pickedEndDay >= startMillis && pickedEndDay < endMillis)
        assert(pickedStartDay >= startMillis && pickedStartDay < endMillis)
    }
}
