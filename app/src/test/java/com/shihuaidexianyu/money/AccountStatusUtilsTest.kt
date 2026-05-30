package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.util.AccountStatusUtils
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountStatusUtilsTest {
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
}
