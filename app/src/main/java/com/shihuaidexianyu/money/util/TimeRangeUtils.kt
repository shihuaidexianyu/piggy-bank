package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
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

    fun currentStatsRange(
        period: StatsPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange {
        return when (period) {
            StatsPeriod.WEEK -> currentWeekRange(zoneId, nowMillis)
            StatsPeriod.MONTH -> currentMonthRange(zoneId, nowMillis)
            StatsPeriod.YEAR -> currentYearRange(zoneId, nowMillis)
        }
    }

    fun currentYearRange(
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = nowDate.withDayOfYear(1)
        val end = nowDate.withDayOfYear(nowDate.lengthOfYear())
        return buildRange(start, end, zoneId)
    }

    data class SubPeriod(
        val label: String,
        val range: TimeRange,
    )

    fun splitIntoPeriods(
        period: StatsPeriod,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): List<SubPeriod> {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return when (period) {
            StatsPeriod.WEEK -> {
                val monday = nowDate.with(DayOfWeek.MONDAY)
                (0L..6L).map { offset ->
                    val day = monday.plusDays(offset)
                    SubPeriod(
                        label = "${day.monthValue}/${day.dayOfMonth}",
                        range = buildRange(day, day, zoneId),
                    )
                }.filter { it.range.startAtMillis <= nowMillis }
            }
            StatsPeriod.MONTH -> {
                val start = nowDate.withDayOfMonth(1)
                val daysInMonth = nowDate.lengthOfMonth()
                (0 until daysInMonth).map { offset ->
                    val day = start.plusDays(offset.toLong())
                    SubPeriod(
                        label = "${day.dayOfMonth}",
                        range = buildRange(day, day, zoneId),
                    )
                }.filter { it.range.startAtMillis <= nowMillis }
            }
            StatsPeriod.YEAR -> {
                (1..12).map { month ->
                    val monthStart = LocalDate.of(nowDate.year, month, 1)
                    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
                    SubPeriod(
                        label = "${month}月",
                        range = buildRange(monthStart, monthEnd, zoneId),
                    )
                }.filter { it.range.startAtMillis <= nowMillis }
            }
        }
    }
}

