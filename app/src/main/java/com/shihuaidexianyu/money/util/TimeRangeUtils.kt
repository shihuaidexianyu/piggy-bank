package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.HomePeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TimeRange(
    val startAtMillis: Long,
    val endAtMillis: Long,
)

object TimeRangeUtils {
    fun currentRange(
        period: HomePeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange {
        return when (period) {
            HomePeriod.WEEK -> currentWeekRange(zoneId, nowMillis)
            HomePeriod.MONTH -> currentMonthRange(zoneId, nowMillis)
        }
    }

    fun currentWeekRange(
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.with(DayOfWeek.MONDAY)
        val end = start.plusDays(6)
        return buildRange(start, end, zoneId)
    }

    fun currentMonthRange(
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.withDayOfMonth(1)
        val end = nowDate.withDayOfMonth(nowDate.lengthOfMonth())
        return buildRange(start, end, zoneId)
    }

    private fun buildRange(start: LocalDate, end: LocalDate, zoneId: ZoneId): TimeRange {
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return TimeRange(startMillis, endMillis)
    }
}
