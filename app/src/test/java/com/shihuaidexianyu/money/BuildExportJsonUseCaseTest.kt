package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BuildExportJsonUseCaseTest {
    @Test
    fun `export json includes metadata settings records reminders and deletion markers`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "现金",
                initialBalance = 12_345,
                createdAt = 1L,
                colorName = "green",
                iconName = "cash",
            ),
        )
        val archivedAccountId = accountRepository.createAccount(
            Account(
                name = "旧账户",
                initialBalance = 9_999,
                createdAt = 2L,
                colorName = "red",
            ),
        )
        accountRepository.archiveAccount(archivedAccountId, 9L)
        reminderSettingsRepository.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(
                period = BalanceUpdateReminderPeriod.MONTHLY,
                weekday = BalanceUpdateReminderWeekday.MONDAY,
                monthDay = 28,
                hour = 8,
                minute = 30,
            ),
        )
        val deletedCashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 123,
                note = "早餐",
                occurredAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
                operationId = testOperationId(),
            ),
        ).recordId
        transactionRepository.softDeleteCashFlowRecord(deletedCashFlowId, 4L)
        val deletedTransferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = accountId,
                toAccountId = archivedAccountId,
                amount = 456,
                note = "转出",
                occurredAt = 4L,
                createdAt = 4L,
                updatedAt = 4L,
                operationId = testOperationId(),
            ),
        ).recordId
        transactionRepository.softDeleteTransferRecord(deletedTransferId, 5L)
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 12_000,
                systemBalanceBeforeUpdate = 12_345,
                delta = -345,
                occurredAt = 6L,
                createdAt = 6L,
                updatedAt = 6L,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = 100,
                occurredAt = 7L,
                createdAt = 7L,
                updatedAt = 7L,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -50,
                occurredAt = 8L,
                createdAt = 8L,
                updatedAt = 8L,
                operationId = testOperationId(),
            ),
        )
        reminderRepository.insertReminder(
            RecurringReminder(
                name = "订阅",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 888,
                periodType = ReminderPeriodType.MONTHLY.value,
                periodValue = 15,
                periodMonth = null,
                nextDueAt = 10L,
                createdAt = 10L,
                updatedAt = 10L,
                anchorDueAt = 10L,
            ),
        )

        val json = BuildExportJsonUseCase(
            buildExportSnapshotUseCase = BuildExportSnapshotUseCase(
                accountReminderSettingsRepository = reminderSettingsRepository,
                accountRepository = accountRepository,
                recurringReminderRepository = reminderRepository,
                savingsGoalRepository = InMemorySavingsGoalRepository(),
                settingsRepository = TestSettingsRepository(AppSettings(currencySymbol = "元")),
                transactionRepository = transactionRepository,
                databaseVersion = 10,
            ),
            backupJsonEncoder = BackupJsonCodec,
        )(exportedAt = 42L)

        val snapshot = BackupJsonCodec.decode(json)
        assertEquals(3, snapshot.metadata.schemaVersion)
        assertEquals(10, snapshot.metadata.databaseVersion)
        assertEquals(42L, snapshot.metadata.exportedAt)
        assertEquals("元", snapshot.settings.currencySymbol)
        assertEquals(listOf(accountId, archivedAccountId), snapshot.accounts.map { it.id })
        assertTrue(snapshot.accounts.first { it.id == archivedAccountId }.isArchived)
        val account = snapshot.accounts.first { it.id == accountId }
        assertEquals(12_345L, account.initialBalance)
        assertEquals("cash", account.iconName)
        assertEquals(1, snapshot.cashFlowRecords.size)
        assertEquals(123L, snapshot.cashFlowRecords.single().amount)
        assertTrue(snapshot.cashFlowRecords.single().isDeleted)
        assertEquals(1, snapshot.transferRecords.size)
        assertTrue(snapshot.transferRecords.single().isDeleted)
        assertEquals(12_000L, snapshot.balanceUpdateRecords.single().actualBalance)
        assertEquals(listOf(100L, -50L), snapshot.balanceAdjustmentRecords.map { it.delta })
        assertEquals(888L, snapshot.recurringReminders.single().amount)
        val reminderConfig = snapshot.accountReminderConfigs.first { it.accountId == accountId }.config
        assertEquals("monthly", reminderConfig.period)
        assertEquals("monday", reminderConfig.weekday)
        assertEquals(28, reminderConfig.monthDay)
        assertEquals(8, reminderConfig.hour)
        assertEquals(30, reminderConfig.minute)
    }
}
