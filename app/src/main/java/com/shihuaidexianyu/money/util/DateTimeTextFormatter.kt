package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.TimeMath
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeTextFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.SIMPLIFIED_CHINESE)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)

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

    /**
     * Due-reminder style label: "今天 HH:mm" on [nowMillis]'s day, "明天 HH:mm" on the next day,
     * otherwise "M月d日 HH:mm". Day labels arrive as parameters so user-facing strings stay in
     * resources; the "M月d日" pattern mirrors the history day-label style.
     */
    fun formatRelativeDayTime(
        timeMillis: Long,
        nowMillis: Long,
        todayLabel: String,
        tomorrowLabel: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = Instant.ofEpochMilli(timeMillis).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val time = Instant.ofEpochMilli(timeMillis).atZone(zoneId).format(timeFormatter)
        return when (day) {
            today -> "$todayLabel $time"
            today.plusDays(1) -> "$tomorrowLabel $time"
            else -> "${day.monthValue}月${day.dayOfMonth}日 $time"
        }
    }

    /**
     * HistoryDayLabel-style calendar date without the 今天/昨天 classification: "M月d日" when the
     * date falls inside [nowMillis]'s year, "yyyy年M月d日" otherwise.
     */
    fun formatDayInYear(
        timeMillis: Long,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = Instant.ofEpochMilli(timeMillis).atZone(zoneId).toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return if (day.year == now.year) {
            "${day.monthValue}月${day.dayOfMonth}日"
        } else {
            "${day.year}年${day.monthValue}月${day.dayOfMonth}日"
        }
    }

    /**
     * Material 3's date picker encodes a calendar date as midnight UTC rather than as an instant
     * in the device time zone. Convert a real timestamp before handing it to the picker so dates
     * near local midnight do not appear as the previous or next day.
     */
    fun toDatePickerMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    /** Converts Material 3's UTC date encoding back to local midnight on the selected date. */
    fun fromDatePickerMillis(
        selectedDateMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(selectedDateMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(zoneId)
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


