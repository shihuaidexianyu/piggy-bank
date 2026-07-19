package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Domain policy: computes [TimeRange]s for dashboard periods. Moved out of `util/`
 * to break the `domain ↔ util` package cycle.
 */
object TimeRangeCalculator {
    fun currentMonthRange(
        zoneId: ZoneId,
        nowMillis: Long,
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.withDayOfMonth(1)
        return buildRange(start, start.plusMonths(1), zoneId)
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
