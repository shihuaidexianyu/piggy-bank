package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Domain policy: decides whether an account is "stale" with respect to its
 * balance-update reminder schedule. Moved out of `util/` to break the
 * `domain ↔ util` package cycle.
 */
object AccountStatusCalculator {
    fun isStale(
        account: Account,
        reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        nowMillis: Long,
        zoneId: ZoneId,
    ): Boolean {
        if (!reminderConfig.isEnabled) return false
        val anchor = account.lastBalanceUpdateAt ?: account.createdAt
        val latestReminderAt = latestReminderBoundaryAt(nowMillis, reminderConfig, zoneId)
        return anchor < latestReminderAt
    }

    fun latestReminderBoundaryAt(
        nowMillis: Long,
        reminderConfig: BalanceUpdateReminderConfig,
        zoneId: ZoneId,
    ): Long {
        val currentDateTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDateTime()
        val reminderTime = LocalTime.of(reminderConfig.hour, reminderConfig.minute)
        return when (reminderConfig.period) {
            BalanceUpdateReminderPeriod.WEEKLY -> latestWeeklyReminderAt(currentDateTime, reminderConfig, reminderTime, zoneId)
            BalanceUpdateReminderPeriod.MONTHLY -> latestMonthlyReminderAt(currentDateTime, reminderConfig, reminderTime, zoneId)
        }
    }

    private fun latestWeeklyReminderAt(
        currentDateTime: LocalDateTime,
        reminderConfig: BalanceUpdateReminderConfig,
        reminderTime: LocalTime,
        zoneId: ZoneId,
    ): Long {
        val currentDate = currentDateTime.toLocalDate()
        val targetValue = when (reminderConfig.weekday) {
            BalanceUpdateReminderWeekday.MONDAY -> 1
            BalanceUpdateReminderWeekday.TUESDAY -> 2
            BalanceUpdateReminderWeekday.WEDNESDAY -> 3
            BalanceUpdateReminderWeekday.THURSDAY -> 4
            BalanceUpdateReminderWeekday.FRIDAY -> 5
            BalanceUpdateReminderWeekday.SATURDAY -> 6
            BalanceUpdateReminderWeekday.SUNDAY -> 7
        }
        var daysSince = Math.floorMod(currentDate.dayOfWeek.value - targetValue, 7)
        if (daysSince == 0 && currentDateTime.toLocalTime().isBefore(reminderTime)) {
            daysSince = 7
        }
        return currentDate
            .minusDays(daysSince.toLong())
            .atTime(reminderTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun latestMonthlyReminderAt(
        currentDateTime: LocalDateTime,
        reminderConfig: BalanceUpdateReminderConfig,
        reminderTime: LocalTime,
        zoneId: ZoneId,
    ): Long {
        val currentDate = currentDateTime.toLocalDate()
        val thisMonthReminderDate = reminderDateInMonth(currentDate, reminderConfig.monthDay)
        val thisMonthReminder = thisMonthReminderDate.atTime(reminderTime)
        val latestDate = if (currentDateTime.isBefore(thisMonthReminder)) {
            reminderDateInMonth(currentDate.minusMonths(1), reminderConfig.monthDay)
        } else {
            thisMonthReminderDate
        }
        return latestDate
            .atTime(reminderTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun reminderDateInMonth(date: LocalDate, dayOfMonth: Int): LocalDate {
        return date.withDayOfMonth(dayOfMonth.coerceIn(1, date.lengthOfMonth()))
    }
}
