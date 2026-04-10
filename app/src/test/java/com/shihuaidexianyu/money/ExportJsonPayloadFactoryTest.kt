package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.ExportJsonPayloadFactory
import kotlin.test.assertEquals
import org.junit.Test

class ExportJsonPayloadFactoryTest {
    @Test
    fun `payload factory keeps all required sections and sorts records by time`() {
        val payload = ExportJsonPayloadFactory.build(
            accounts = listOf(
                AccountEntity(id = 2, name = "旧账户", groupType = "bank", initialBalance = 0, createdAt = 1, isArchived = true),
                AccountEntity(id = 1, name = "主账户", groupType = "payment", initialBalance = 0, createdAt = 1, displayOrder = 2),
            ),
            accountReminderConfigs = mapOf(
                1L to BalanceUpdateReminderConfig(
                    weekday = BalanceUpdateReminderWeekday.WEDNESDAY,
                    hour = 18,
                    minute = 45,
                ),
            ),
            cashFlowRecords = listOf(
                CashFlowRecordEntity(id = 2, accountId = 1, direction = "inflow", amount = 200, purpose = "后", occurredAt = 20, createdAt = 20, updatedAt = 20),
                CashFlowRecordEntity(id = 1, accountId = 1, direction = "inflow", amount = 100, purpose = "前", occurredAt = 10, createdAt = 10, updatedAt = 10),
            ),
            transferRecords = listOf(
                TransferRecordEntity(id = 1, fromAccountId = 1, toAccountId = 2, amount = 100, note = "", occurredAt = 30, createdAt = 30, updatedAt = 30),
            ),
            balanceUpdateRecords = listOf(
                BalanceUpdateRecordEntity(id = 1, accountId = 1, actualBalance = 100, systemBalanceBeforeUpdate = 90, delta = 10, occurredAt = 40, createdAt = 40),
            ),
            balanceAdjustmentRecords = listOf(
                BalanceAdjustmentRecordEntity(id = 1, accountId = 1, delta = 10, sourceUpdateRecordId = 1, occurredAt = 40, createdAt = 40),
            ),
            settings = AppSettings(
                homePeriod = HomePeriod.MONTH,
                currencySymbol = "$",
                showStaleMark = false,
                themeMode = ThemeMode.DARK,
            ),
            exportedAt = 99,
            appVersion = "1.0",
        )

        assertEquals(2, payload.accounts.size)
        assertEquals(
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.WEDNESDAY,
                hour = 18,
                minute = 45,
            ),
            payload.accountReminderConfigs[1L],
        )
        assertEquals("主账户", payload.accounts.first().name)
        assertEquals(10, payload.cashFlowRecords.first().occurredAt)
        assertEquals(1, payload.balanceUpdateRecords.size)
        assertEquals(1, payload.balanceAdjustmentRecords.size)
        assertEquals("$", payload.settings.currencySymbol)
        assertEquals(ThemeMode.DARK, payload.settings.themeMode)
        assertEquals(99, payload.exportedAt)
        assertEquals("1.0", payload.appVersion)
    }
}

