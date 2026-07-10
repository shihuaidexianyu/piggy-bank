package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.util.AccountStatusUtils
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountStatusUtilsTest {
    @Test
    fun `disabled account reminder never marks account stale`() {
        val account = Account(name = "现金", initialBalance = 0L, createdAt = 1L)

        assertFalse(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = BalanceUpdateReminderConfig(isEnabled = false),
                nowMillis = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `account becomes stale after configured weekday time passes`() {
        val zoneId = ZoneId.systemDefault()
        val updatedAt = LocalDateTime.of(2026, 4, 6, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val account = Account(
            id = 1,
            name = "银行卡",
            initialBalance = 0,
            createdAt = updatedAt,
            lastBalanceUpdateAt = updatedAt,
        )

        assertFalse(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = BalanceUpdateReminderConfig(
                    weekday = BalanceUpdateReminderWeekday.FRIDAY,
                    hour = 22,
                    minute = 0,
                ),
                nowMillis = LocalDateTime.of(2026, 4, 10, 21, 59)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            ),
        )

        assertTrue(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = BalanceUpdateReminderConfig(
                    weekday = BalanceUpdateReminderWeekday.FRIDAY,
                    hour = 22,
                    minute = 0,
                ),
                nowMillis = LocalDateTime.of(2026, 4, 10, 22, 1)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            ),
        )
    }

    @Test
    fun `account becomes stale after configured monthly day time passes`() {
        val zoneId = ZoneId.systemDefault()
        val updatedAt = LocalDateTime.of(2026, 4, 10, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val account = Account(
            id = 1,
            name = "银行卡",
            initialBalance = 0,
            createdAt = updatedAt,
            lastBalanceUpdateAt = updatedAt,
        )
        val config = BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.MONTHLY,
            monthDay = 30,
            hour = 22,
            minute = 0,
        )

        assertFalse(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = LocalDateTime.of(2026, 4, 30, 21, 59)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            ),
        )

        assertTrue(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = LocalDateTime.of(2026, 4, 30, 22, 1)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            ),
        )
    }

    @Test
    fun `monthly reminder day falls back to month end when day does not exist`() {
        val zoneId = ZoneId.systemDefault()
        val updatedAt = LocalDateTime.of(2026, 2, 28, 21, 59)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val account = Account(
            id = 1,
            name = "银行卡",
            initialBalance = 0,
            createdAt = updatedAt,
            lastBalanceUpdateAt = updatedAt,
        )
        val config = BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.MONTHLY,
            monthDay = 31,
            hour = 22,
            minute = 0,
        )

        assertTrue(
            AccountStatusUtils.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = LocalDateTime.of(2026, 2, 28, 22, 1)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            ),
        )
    }
}
