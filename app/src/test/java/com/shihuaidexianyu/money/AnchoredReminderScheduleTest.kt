package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.usecase.ReminderNextDueCalculator
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AnchoredReminderScheduleTest {
    @Test
    fun `default anchor is the nearest future minute`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 1, 2, 10, 20, 35, 123, zone).toInstant().toEpochMilli()

        val anchor = ReminderNextDueCalculator.defaultFutureAnchor(now, zone)

        assertEquals(
            ZonedDateTime.of(2026, 1, 2, 10, 21, 0, 0, zone).toInstant().toEpochMilli(),
            anchor,
        )
        assertTrue(anchor > now)
    }

    @Test
    fun `monthly anchor on 31st clamps and retains local time`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val anchor = at(zone, 2025, 1, 31, 18, 45)

        val next = ReminderNextDueCalculator.calculateNextDue(
            currentDueAt = anchor,
            anchorDueAt = anchor,
            periodType = ReminderPeriodType.MONTHLY,
            periodValue = 31,
            periodMonth = null,
            zoneId = zone,
        )

        assertEquals(at(zone, 2025, 2, 28, 18, 45), next)
    }

    @Test
    fun `migration shaped clamped monthly anchor retains configured day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val migratedAnchor = at(zone, 2025, 2, 28, 18, 45)

        val next = ReminderNextDueCalculator.calculateNextDue(
            migratedAnchor,
            migratedAnchor,
            ReminderPeriodType.MONTHLY,
            31,
            null,
            zone,
        )

        assertEquals(at(zone, 2025, 3, 31, 18, 45), next)
    }

    @Test
    fun `migration shaped non leap anchor retains configured leap day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val migratedAnchor = at(zone, 2025, 2, 28, 7, 5)

        val next = ReminderNextDueCalculator.calculateNextDue(
            migratedAnchor,
            migratedAnchor,
            ReminderPeriodType.YEARLY,
            29,
            2,
            zone,
        )

        assertEquals(at(zone, 2026, 2, 28, 7, 5), next)
    }

    @Test
    fun `yearly leap anchor clamps non leap year and returns to leap day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val anchor = at(zone, 2024, 2, 29, 7, 5)
        val nonLeap = ReminderNextDueCalculator.calculateNextDue(
            anchor, anchor, ReminderPeriodType.YEARLY, 29, 2, zone,
        )
        val leapAgain = ReminderNextDueCalculator.calculateNextDue(
            at(zone, 2027, 2, 28, 7, 5), anchor, ReminderPeriodType.YEARLY, 29, 2, zone,
        )

        assertEquals(at(zone, 2025, 2, 28, 7, 5), nonLeap)
        assertEquals(at(zone, 2028, 2, 29, 7, 5), leapAgain)
    }

    @Test
    fun `custom days uses local calendar across DST`() {
        val zone = ZoneId.of("America/New_York")
        val anchor = at(zone, 2025, 3, 8, 9, 30)

        val next = ReminderNextDueCalculator.calculateNextDue(
            anchor, anchor, ReminderPeriodType.CUSTOM_DAYS, 1, null, zone,
        )

        assertEquals(at(zone, 2025, 3, 9, 9, 30), next)
        assertEquals(23L * 60L * 60L * 1_000L, next - anchor)
    }

    @Test
    fun `same stored anchor is interpreted in current device zone`() {
        val anchor = at(ZoneId.of("Asia/Shanghai"), 2025, 1, 31, 9, 0)

        val nextInShanghai = ReminderNextDueCalculator.calculateNextDue(
            anchor, anchor, ReminderPeriodType.MONTHLY, 31, null, ZoneId.of("Asia/Shanghai"),
        )
        val nextInNewYork = ReminderNextDueCalculator.calculateNextDue(
            anchor, anchor, ReminderPeriodType.MONTHLY, 31, null, ZoneId.of("America/New_York"),
        )

        assertTrue(nextInShanghai != nextInNewYork)
    }

    private fun at(
        zone: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
