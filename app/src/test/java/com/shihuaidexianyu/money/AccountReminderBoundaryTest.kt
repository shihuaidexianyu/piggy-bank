package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.usecase.AccountStatusCalculator
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import kotlin.test.assertEquals

class AccountReminderBoundaryTest {
    @Test
    fun `stale boundary uses configured zone across DST`() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2025, 3, 10, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val config = BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.MONTHLY,
            monthDay = 9,
            hour = 22,
            minute = 30,
        )

        val boundary = AccountStatusCalculator.latestReminderBoundaryAt(now, config, zone)

        assertEquals(
            ZonedDateTime.of(2025, 3, 9, 22, 30, 0, 0, zone).toInstant().toEpochMilli(),
            boundary,
        )
    }
}
