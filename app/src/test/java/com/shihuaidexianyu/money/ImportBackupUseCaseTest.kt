package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupAccountReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupCashFlowRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupRecurringReminder
import com.shihuaidexianyu.money.domain.model.backup.BackupSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.usecase.ImportBackupUseCase
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ImportBackupUseCaseTest {
    @Test
    fun `valid snapshot is validated before replace`() = runBlocking {
        val repository = FakeBackupRepository()
        val snapshot = validSnapshot()
        val useCase = ImportBackupUseCase(
            backupRepository = repository,
            validateBackupSnapshotUseCase = ValidateBackupSnapshotUseCase(),
        )

        val result = useCase(snapshot)

        assertEquals(snapshot, repository.replacedSnapshot)
        assertEquals(1, result.accountCount)
        assertEquals(1, result.cashFlowCount)
        assertEquals(1, result.transferCount)
        assertEquals(1, result.balanceUpdateCount)
        assertEquals(1, result.balanceAdjustmentCount)
        assertEquals(1, result.reminderCount)
        assertEquals(42L, result.exportedAt)
    }

    @Test
    fun `invalid reference fails without replacing data`() = runBlocking {
        val repository = FakeBackupRepository()
        val useCase = ImportBackupUseCase(repository, ValidateBackupSnapshotUseCase())
        val invalid = validSnapshot().copy(
            cashFlowRecords = listOf(
                validSnapshot().cashFlowRecords.single().copy(accountId = 99L),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(invalid)
        }

        assertNull(repository.replacedSnapshot)
    }

    @Test
    fun `duplicate account ids are rejected`() {
        val snapshot = validSnapshot()
        val invalid = snapshot.copy(accounts = snapshot.accounts + snapshot.accounts.single())

        assertFailsWith<IllegalArgumentException> {
            ValidateBackupSnapshotUseCase()(invalid)
        }
    }

    @Test
    fun `unknown enums and negative amounts are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ValidateBackupSnapshotUseCase()(
                validSnapshot().copy(
                    recurringReminders = listOf(validSnapshot().recurringReminders.single().copy(periodType = "weekly")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ValidateBackupSnapshotUseCase()(
                validSnapshot().copy(
                    transferRecords = listOf(validSnapshot().transferRecords.single().copy(amount = -1L)),
                ),
            )
        }
    }

    private class FakeBackupRepository : BackupRepository {
        var replacedSnapshot: MoneyBackupSnapshot? = null

        override suspend fun replaceAll(snapshot: MoneyBackupSnapshot) {
            replacedSnapshot = snapshot
        }
    }

    private fun validSnapshot(): MoneyBackupSnapshot {
        return MoneyBackupSnapshot(
            metadata = BackupMetadata(
                schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
                databaseVersion = 7,
                exportedAt = 42L,
            ),
            settings = BackupSettings(
                homePeriod = "week",
                currencySymbol = "¥",
                showStaleMark = true,
                themeMode = "system",
                amountColorMode = "red_income_green_expense",
                lastHistoryKeyword = "",
                lastHistoryAccountId = -1L,
                lastHistoryDateStartAt = -1L,
                lastHistoryDateEndAt = -1L,
                lastHistoryMinAmountText = "",
                lastHistoryMaxAmountText = "",
                lastHistoryAmountDirection = "all",
            ),
            accounts = listOf(
                BackupAccount(
                    id = 1L,
                    name = "现金",
                    initialBalance = 10_000L,
                    createdAt = 1L,
                    archivedAt = null,
                    isArchived = false,
                    lastUsedAt = null,
                    lastBalanceUpdateAt = null,
                    displayOrder = 0,
                    colorName = "blue",
                ),
            ),
            cashFlowRecords = listOf(
                BackupCashFlowRecord(
                    id = 1L,
                    accountId = 1L,
                    direction = "outflow",
                    amount = 100L,
                    purpose = "早餐",
                    occurredAt = 2L,
                    createdAt = 2L,
                    updatedAt = 2L,
                    isDeleted = false,
                ),
            ),
            transferRecords = listOf(
                BackupTransferRecord(
                    id = 1L,
                    fromAccountId = 1L,
                    toAccountId = 1L,
                    amount = 50L,
                    note = "内部校正",
                    occurredAt = 3L,
                    createdAt = 3L,
                    updatedAt = 3L,
                    isDeleted = false,
                ),
            ),
            balanceUpdateRecords = listOf(
                BackupBalanceUpdateRecord(
                    id = 1L,
                    accountId = 1L,
                    actualBalance = 9_900L,
                    systemBalanceBeforeUpdate = 9_950L,
                    delta = -50L,
                    occurredAt = 4L,
                    createdAt = 4L,
                ),
            ),
            balanceAdjustmentRecords = listOf(
                BackupBalanceAdjustmentRecord(
                    id = 1L,
                    accountId = 1L,
                    delta = 20L,
                    occurredAt = 5L,
                    createdAt = 5L,
                ),
            ),
            recurringReminders = listOf(
                BackupRecurringReminder(
                    id = 1L,
                    name = "订阅",
                    type = "subscription",
                    accountId = 1L,
                    direction = "outflow",
                    amount = 888L,
                    periodType = "monthly",
                    periodValue = 9,
                    periodMonth = null,
                    isEnabled = true,
                    nextDueAt = 6L,
                    lastConfirmedAt = null,
                    createdAt = 6L,
                    updatedAt = 6L,
                ),
            ),
            accountReminderConfigs = listOf(
                BackupAccountReminderConfig(
                    accountId = 1L,
                    config = BackupBalanceUpdateReminderConfig(
                        weekday = "friday",
                        hour = 22,
                        minute = 0,
                    ),
                ),
            ),
        )
    }
}
