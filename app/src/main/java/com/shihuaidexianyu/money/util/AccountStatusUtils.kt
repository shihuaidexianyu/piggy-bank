package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AccountStatusUtils {
    fun isStale(
        account: Account,
        reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val anchor = account.lastBalanceUpdateAt ?: account.createdAt
        val latestReminderAt = latestReminderAt(nowMillis, reminderConfig)
        return anchor < latestReminderAt
    }

    private fun latestReminderAt(
        nowMillis: Long,
        reminderConfig: BalanceUpdateReminderConfig,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val currentDateTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDateTime()
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
        val reminderTime = LocalTime.of(reminderConfig.hour, reminderConfig.minute)
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
}
