package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HistoryDayLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class HistoryDayLabelTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    @Test
    fun `same local day classifies as today`() {
        val now = at(2026, 7, 17, 8, 30)

        assertEquals(
            HistoryDayLabel.Today,
            HistoryDayLabel.classify(occurredAt = at(2026, 7, 17, 0, 5), nowMillis = now, zoneId = zone),
        )
        assertEquals(
            HistoryDayLabel.Today,
            HistoryDayLabel.classify(occurredAt = at(2026, 7, 17, 23, 59), nowMillis = now, zoneId = zone),
        )
    }

    @Test
    fun `previous local day classifies as yesterday`() {
        val now = at(2026, 7, 17, 12, 0)

        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(occurredAt = at(2026, 7, 16, 0, 0), nowMillis = now, zoneId = zone),
        )
        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(occurredAt = at(2026, 7, 16, 23, 59), nowMillis = now, zoneId = zone),
        )
    }

    @Test
    fun `yesterday classification holds across month and year boundaries`() {
        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(
                occurredAt = at(2026, 2, 28, 23, 50),
                nowMillis = at(2026, 3, 1, 0, 10),
                zoneId = zone,
            ),
        )
        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(
                occurredAt = at(2025, 12, 31, 23, 55),
                nowMillis = at(2026, 1, 1, 0, 5),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun `near midnight minutes do not shift the local day`() {
        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(
                occurredAt = at(2026, 7, 16, 23, 59),
                nowMillis = at(2026, 7, 17, 0, 0),
                zoneId = zone,
            ),
        )
        assertEquals(
            HistoryDayLabel.Today,
            HistoryDayLabel.classify(
                occurredAt = at(2026, 7, 17, 0, 1),
                nowMillis = at(2026, 7, 17, 23, 59),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun `older days in the same year expose month and day`() {
        val now = at(2026, 7, 17, 12, 0)

        assertEquals(
            HistoryDayLabel.SameYear(month = 7, dayOfMonth = 15),
            HistoryDayLabel.classify(occurredAt = at(2026, 7, 15, 12, 0), nowMillis = now, zoneId = zone),
        )
        assertEquals(
            HistoryDayLabel.SameYear(month = 2, dayOfMonth = 3),
            HistoryDayLabel.classify(occurredAt = at(2026, 2, 3, 9, 0), nowMillis = now, zoneId = zone),
        )
        assertEquals(
            HistoryDayLabel.SameYear(month = 1, dayOfMonth = 1),
            HistoryDayLabel.classify(occurredAt = at(2026, 1, 1, 0, 0), nowMillis = now, zoneId = zone),
        )
    }

    @Test
    fun `days in another year expose year month and day`() {
        val now = at(2026, 7, 17, 12, 0)

        assertEquals(
            HistoryDayLabel.OtherYear(year = 2025, month = 12, dayOfMonth = 31),
            HistoryDayLabel.classify(occurredAt = at(2025, 12, 31, 12, 0), nowMillis = now, zoneId = zone),
        )
        assertEquals(
            HistoryDayLabel.OtherYear(year = 2024, month = 7, dayOfMonth = 17),
            HistoryDayLabel.classify(occurredAt = at(2024, 7, 17, 12, 0), nowMillis = now, zoneId = zone),
        )
    }

    @Test
    fun `classification follows the local day of the supplied zone`() {
        // 2026-07-16T16:30Z is 2026-07-17 00:30 in Asia/Shanghai but 2026-07-16 in UTC.
        val occurredAt = Instant.parse("2026-07-16T16:30:00Z").toEpochMilli()

        assertEquals(
            HistoryDayLabel.Today,
            HistoryDayLabel.classify(
                occurredAt = occurredAt,
                nowMillis = at(2026, 7, 17, 12, 0),
                zoneId = zone,
            ),
        )
        assertEquals(
            HistoryDayLabel.Yesterday,
            HistoryDayLabel.classify(
                occurredAt = occurredAt,
                nowMillis = Instant.parse("2026-07-17T12:00:00Z").toEpochMilli(),
                zoneId = ZoneId.of("UTC"),
            ),
        )
    }
}
