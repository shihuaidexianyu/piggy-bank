package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.TimeRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Domain policy: computes [TimeRange]s for dashboard periods. Moved out of `util/`
 * to break the `domain ↔ util` package cycle.
 */
object TimeRangeCalculator {
    fun currentRange(
        period: HomePeriod,
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        return when (period) {
            HomePeriod.WEEK -> currentWeekRange(zoneId, nowMillis)
            HomePeriod.MONTH -> currentMonthRange(zoneId, nowMillis)
        }
    }

    fun currentWeekRange(
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.with(DayOfWeek.MONDAY)
        return buildRange(start, start.plusWeeks(1), zoneId)
    }

    fun currentMonthRange(
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.withDayOfMonth(1)
        return buildRange(start, start.plusMonths(1), zoneId)
    }

    fun currentStatsRange(
        period: StatsPeriod,
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        return statsRange(period, zoneId, nowMillis)
    }

    fun statsRange(
        period: StatsPeriod,
        zoneId: ZoneId,
        anchorMillis: Long,
    ): TimeRange {
        return when (period) {
            StatsPeriod.WEEK -> currentWeekRange(zoneId, anchorMillis)
            StatsPeriod.MONTH -> currentMonthRange(zoneId, anchorMillis)
            StatsPeriod.YEAR -> currentYearRange(zoneId, anchorMillis)
        }
    }

    fun currentYearRange(
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.withDayOfYear(1)
        return buildRange(start, start.plusYears(1), zoneId)
    }

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
