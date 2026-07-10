package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ReminderNextDueCalculator {

    /**
     * Reminder timestamps intentionally have no stored timezone. Every calculation interprets the
     * anchor in the currently injected device zone, so changing the device timezone can change the
     * future UTC instants while retaining the newly interpreted local calendar schedule.
     */
    fun defaultFutureAnchor(nowMillis: Long, zoneId: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val minute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        return minute.toInstant().toEpochMilli()
    }

    fun calculateNextDue(
        currentDueAt: Long,
        anchorDueAt: Long,
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
        zoneId: ZoneId,
    ): Long {
        val current = Instant.ofEpochMilli(currentDueAt).atZone(zoneId)
        val anchor = Instant.ofEpochMilli(anchorDueAt).atZone(zoneId)
        val anchorTime = anchor.toLocalTime().withSecond(0).withNano(0)
        val nextDate = when (periodType) {
            ReminderPeriodType.MONTHLY -> {
                val nextMonth = current.toLocalDate().plusMonths(1).withDayOfMonth(1)
                nextMonth.withDayOfMonth(periodValue.coerceIn(1, nextMonth.lengthOfMonth()))
            }
            ReminderPeriodType.YEARLY -> {
                val nextYear = current.year + 1
                val month = (periodMonth ?: anchor.monthValue).coerceIn(1, 12)
                val firstOfMonth = LocalDate.of(nextYear, month, 1)
                firstOfMonth.withDayOfMonth(periodValue.coerceIn(1, firstOfMonth.lengthOfMonth()))
            }
            ReminderPeriodType.CUSTOM_DAYS ->
                current.toLocalDate().plusDays(periodValue.toLong().coerceAtLeast(1))
        }
        return nextDate.atTime(anchorTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun firstOccurrenceAtOrAfter(
        anchorDueAt: Long,
        cutoffMillis: Long,
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
        zoneId: ZoneId,
    ): Long {
        var occurrence = anchorDueAt
        while (occurrence < cutoffMillis) {
            val next = calculateNextDue(
                currentDueAt = occurrence,
                anchorDueAt = anchorDueAt,
                periodType = periodType,
                periodValue = periodValue,
                periodMonth = periodMonth,
                zoneId = zoneId,
            )
            check(next > occurrence) { "提醒周期没有向未来推进" }
            occurrence = next
        }
        return occurrence
    }
}

