package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.TimeRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Domain policy: computes [TimeRange]s for dashboard periods. Moved out of `util/`
 * to break the `domain ↔ util` package cycle.
 */
object TimeRangeCalculator {
    fun currentMonthRange(
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val nowDate = localDate(zoneId, nowMillis)
        val start = nowDate.withDayOfMonth(1)
        return buildRange(start, start.plusMonths(1), zoneId)
    }

    /** The range covering [nowMillis] for the given [period]. */
    fun rangeFor(
        period: DashboardPeriod,
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val start = periodStart(period, localDate(zoneId, nowMillis))
        return buildRange(start, nextPeriodStart(period, start), zoneId)
    }

    private fun periodStart(period: DashboardPeriod, date: LocalDate): LocalDate = when (period) {
        // Weeks start on Monday, matching the calendar convention used in Simplified Chinese locales.
        DashboardPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DashboardPeriod.MONTH -> date.withDayOfMonth(1)
        DashboardPeriod.YEAR -> date.withDayOfYear(1)
    }

    private fun nextPeriodStart(period: DashboardPeriod, start: LocalDate): LocalDate = when (period) {
        DashboardPeriod.WEEK -> start.plusWeeks(1)
        DashboardPeriod.MONTH -> start.plusMonths(1)
        DashboardPeriod.YEAR -> start.plusYears(1)
    }

    private fun localDate(zoneId: ZoneId, millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    private fun buildRange(
        startInclusive: LocalDate,
        endExclusive: LocalDate,
        zoneId: ZoneId,
    ): TimeRange {
        return TimeRange(
            startInclusive = startInclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endExclusive = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }
}
