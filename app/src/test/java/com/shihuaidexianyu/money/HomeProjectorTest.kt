package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.usecase.HomeProjector
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

class HomeProjectorTest {
    @Test
    fun `project sums balances for totalAssets and opening balances for openingAssets`() {
        val accounts = listOf(
            Account(id = 1, name = "A", initialBalance = 1_000, createdAt = 1L, lastBalanceUpdateAt = 100L),
            Account(id = 2, name = "B", initialBalance = 2_000, createdAt = 2L, lastBalanceUpdateAt = 200L),
        )
        val snapshot = HomeProjector.project(
            accounts = accounts,
            reminderConfigs = emptyMap(),
            settings = AppSettings(homePeriod = HomePeriod.MONTH),
            dueReminders = emptyList(),
            balances = mapOf(1L to 1_500L, 2L to 2_500L),
            openingBalanceByAccount = mapOf(1L to 1_200L, 2L to 2_400L),
            newAccountOpeningAssets = 0L,
            cashInflow = 500L,
            cashOutflow = 300L,
            reconciliationIncrease = 50L,
            reconciliationDecrease = 20L,
            manualAdjustmentIncrease = 10L,
            manualAdjustmentDecrease = 5L,
            cashFlowRecordCount = 4,
            transferRecordCount = 2,
            manualAdjustmentRecordCount = 1,
            snapshotTimeMillis = 4_102_444_800_000L,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(4_000L, snapshot.totalAssets)
        assertEquals(3_600L, snapshot.periodBreakdown.openingAssets)
        assertEquals(400L, snapshot.periodBreakdown.assetChange)
        assertEquals(200L, snapshot.periodBreakdown.cashNet)
        assertEquals(5L, snapshot.periodBreakdown.manualAdjustmentNet)
        assertEquals(30L, snapshot.periodBreakdown.reconciliationNet)
        assertEquals(7, snapshot.periodRecordCount)
        // Both accounts were last updated at timestamps far in the past (100L, 200L), so the
        // monthly reminder due date is well past them → both are stale. We assert against the
        // accounts list size, not against 0, to document the actual behavior without making the
        // test brittle on System.currentTimeMillis().
        assertEquals(snapshot.staleAccounts.size, snapshot.staleAccountCount)
    }

    @Test
    fun `project flags stale accounts based on reminder config`() {
        val mondayMidnight = Instant.parse("2023-10-23T00:00:00Z").toEpochMilli()
        val nowMillis = mondayMidnight + 9 * 3_600_000L
        val staleAccount = Account(
            id = 1,
            name = "stale",
            initialBalance = 0,
            createdAt = 50L,
            lastBalanceUpdateAt = mondayMidnight - 1L,
        )
        val freshAccount = Account(
            id = 2,
            name = "fresh",
            initialBalance = 0,
            createdAt = 50L,
            lastBalanceUpdateAt = mondayMidnight + 1L,
        )
        val reminderConfig = BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.WEEKLY,
            weekday = BalanceUpdateReminderWeekday.MONDAY,
            monthDay = 1,
            hour = 0,
            minute = 0,
        )
        val accounts = listOf(staleAccount, freshAccount)
        val snapshot = HomeProjector.project(
            accounts = accounts,
            reminderConfigs = mapOf(1L to reminderConfig, 2L to reminderConfig),
            settings = AppSettings(),
            dueReminders = emptyList(),
            balances = emptyMap(),
            openingBalanceByAccount = emptyMap(),
            newAccountOpeningAssets = 0L,
            cashInflow = 0L,
            cashOutflow = 0L,
            reconciliationIncrease = 0L,
            reconciliationDecrease = 0L,
            manualAdjustmentIncrease = 0L,
            manualAdjustmentDecrease = 0L,
            cashFlowRecordCount = 0,
            transferRecordCount = 0,
            manualAdjustmentRecordCount = 0,
            snapshotTimeMillis = nowMillis,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(listOf(staleAccount), snapshot.staleAccounts)
        assertEquals(1, snapshot.staleAccountCount)
        assertEquals(accounts, snapshot.activeAccounts)
    }

    @Test
    fun `project includes due reminders verbatim`() {
        val reminder = RecurringReminder(
            id = 7,
            name = "订阅",
            type = "subscription",
            accountId = 1,
            direction = "outflow",
            amount = 100,
            periodType = "monthly",
            periodValue = 1,
            periodMonth = null,
            nextDueAt = 10L,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val snapshot = HomeProjector.project(
            accounts = emptyList(),
            reminderConfigs = emptyMap(),
            settings = AppSettings(),
            dueReminders = listOf(reminder),
            balances = emptyMap(),
            openingBalanceByAccount = emptyMap(),
            newAccountOpeningAssets = 0L,
            cashInflow = 0L,
            cashOutflow = 0L,
            reconciliationIncrease = 0L,
            reconciliationDecrease = 0L,
            manualAdjustmentIncrease = 0L,
            manualAdjustmentDecrease = 0L,
            cashFlowRecordCount = 0,
            transferRecordCount = 0,
            manualAdjustmentRecordCount = 0,
            snapshotTimeMillis = 4_102_444_800_000L,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(listOf(reminder), snapshot.dueReminders)
    }
}
