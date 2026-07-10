package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.TimeMath
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeTextFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun format(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(formatter)
    }

    /**
     * Delegates to [TimeMath.floorToMinute] so domain code can use the domain-owned
     * helper without importing `util/`. Kept for UI callers.
     */
    fun floorToMinute(timeMillis: Long): Long = TimeMath.floorToMinute(timeMillis)

    fun formatDateOnly(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(dateFormatter)
    }

    fun formatTimeOnly(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(timeFormatter)
    }

    fun replaceDate(
        baseTimeMillis: Long,
        selectedDateMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val baseDateTime = Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId).toLocalDateTime()
        val selectedDate = Instant.ofEpochMilli(selectedDateMillis).atZone(zoneId).toLocalDate()
        return selectedDate
            .atTime(baseDateTime.toLocalTime().withSecond(0).withNano(0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun replaceTime(
        baseTimeMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val baseDate = Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId).toLocalDate()
        return baseDate
            .atTime(hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun startOfDayMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /** Returns the first instant of the day after [timeMillis]'s local date. */
    fun endExclusiveOfDayMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun startOfDisplayedEndDateMillis(
        endExclusive: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val endDateTime = Instant.ofEpochMilli(endExclusive).atZone(zoneId)
        val endDate = endDateTime.toLocalDate()
        val endDateStart = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val displayedDate = if (endExclusive == endDateStart) endDate.minusDays(1) else endDate
        return displayedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun formatDisplayedEndDate(
        endExclusive: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(startOfDisplayedEndDateMillis(endExclusive, zoneId))
            .atZone(zoneId)
            .format(dateFormatter)
    }
}


