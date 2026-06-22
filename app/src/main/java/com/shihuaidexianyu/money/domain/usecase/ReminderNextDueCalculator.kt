package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderNextDueCalculator {

    /**
     * Default time-of-day applied to every reminder's due date. 09:00 local time is a reasonable
     * "morning reminder" slot. Made a named constant instead of an inline `LocalTime.of(9, 0)` magic
     * value so it's easy to find/change.
     */
    private val DEFAULT_DUE_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

    fun calculateFirstDue(
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
    ): Long {
        val today = LocalDate.now()
        val candidate = when (periodType) {
            ReminderPeriodType.MONTHLY -> {
                val dayOfMonth = periodValue.coerceIn(1, today.lengthOfMonth())
                val thisMonth = today.withDayOfMonth(dayOfMonth)
                if (thisMonth >= today) thisMonth else thisMonth.plusMonths(1)
                    .withDayOfMonth(periodValue.coerceIn(1, thisMonth.plusMonths(1).lengthOfMonth()))
            }
            ReminderPeriodType.YEARLY -> {
                val month = (periodMonth ?: 1).coerceIn(1, 12)
                val maxDay = LocalDate.of(today.year, month, 1).lengthOfMonth()
                val day = periodValue.coerceIn(1, maxDay)
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear >= today) thisYear else {
                    val nextMaxDay = LocalDate.of(today.year + 1, month, 1).lengthOfMonth()
                    LocalDate.of(today.year + 1, month, periodValue.coerceIn(1, nextMaxDay))
                }
            }
            ReminderPeriodType.CUSTOM_DAYS -> {
                today.plusDays(periodValue.toLong().coerceAtLeast(1))
            }
        }
        return candidate.atTime(DEFAULT_DUE_TIME_OF_DAY)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    fun calculateNextDue(
        currentDueAt: Long,
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
    ): Long {
        val currentDate = Instant.ofEpochMilli(currentDueAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val nextDate = when (periodType) {
            ReminderPeriodType.MONTHLY -> {
                val next = currentDate.plusMonths(1)
                next.withDayOfMonth(periodValue.coerceIn(1, next.lengthOfMonth()))
            }
            ReminderPeriodType.YEARLY -> {
                val next = currentDate.plusYears(1)
                val month = (periodMonth ?: next.monthValue).coerceIn(1, 12)
                val maxDay = LocalDate.of(next.year, month, 1).lengthOfMonth()
                LocalDate.of(next.year, month, periodValue.coerceIn(1, maxDay))
            }
            ReminderPeriodType.CUSTOM_DAYS -> {
                currentDate.plusDays(periodValue.toLong().coerceAtLeast(1))
            }
        }
        return nextDate.atTime(DEFAULT_DUE_TIME_OF_DAY)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}

