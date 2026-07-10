package com.shihuaidexianyu.money.data.backup

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupAccountReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupCashFlowRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupPortableSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupRecurringReminder
import com.shihuaidexianyu.money.domain.model.backup.BackupSavingsGoal
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

internal class RoomBackupSnapshotReader(
    private val database: MoneyDatabase,
) {
    suspend fun read(exportedAt: Long): MoneyBackupSnapshot = database.withTransaction {
        readInCurrentTransaction(exportedAt)
    }

    suspend fun readInCurrentTransaction(exportedAt: Long): MoneyBackupSnapshot {
        val accounts = database.accountDao().queryAllAccounts().sortedBy { it.id }
        val configs = database.accountReminderConfigDao().queryAll().associateBy { it.accountId }
        val portable = database.portableSettingsDao().query()
        return MoneyBackupSnapshot(
            metadata = BackupMetadata(MONEY_BACKUP_SCHEMA_VERSION, MONEY_DATABASE_VERSION, exportedAt),
            portableSettings = BackupPortableSettings(
                currencySymbol = portable?.currencySymbol ?: "¥",
                amountColorMode = portable?.amountColorMode ?: AmountColorMode.RED_INCOME_GREEN_EXPENSE.value,
                monthlyBudgetAmount = portable?.monthlyBudgetAmount,
            ),
            accounts = accounts.map {
                BackupAccount(
                    id = it.id,
                    name = it.name,
                    initialBalance = it.initialBalance,
                    createdAt = it.createdAt,
                    isHidden = it.isHidden,
                    closedAt = it.closedAt,
                    lastUsedAt = it.lastUsedAt,
                    lastBalanceUpdateAt = it.lastBalanceUpdateAt,
                    displayOrder = it.displayOrder,
                    colorName = it.colorName,
                    iconName = it.iconName,
                )
            },
            cashFlowRecords = database.cashFlowRecordDao().queryAll().sortedBy { it.id }.map {
                BackupCashFlowRecord(
                    it.id,
                    it.accountId,
                    it.direction,
                    it.amount,
                    it.note,
                    it.occurredAt,
                    it.createdAt,
                    it.updatedAt,
                    it.deletedAt,
                    it.operationId,
                )
            },
            transferRecords = database.transferRecordDao().queryAll().sortedBy { it.id }.map {
                BackupTransferRecord(
                    it.id,
                    it.fromAccountId,
                    it.toAccountId,
                    it.amount,
                    it.note,
                    it.occurredAt,
                    it.createdAt,
                    it.updatedAt,
                    it.deletedAt,
                    it.operationId,
                )
            },
            balanceUpdateRecords = database.balanceUpdateRecordDao().queryAll().sortedBy { it.id }.map {
                BackupBalanceUpdateRecord(
                    it.id,
                    it.accountId,
                    it.actualBalance,
                    it.systemBalanceBeforeUpdate,
                    it.delta,
                    it.occurredAt,
                    it.createdAt,
                    it.updatedAt,
                    it.deletedAt,
                    it.operationId,
                )
            },
            balanceAdjustmentRecords = database.balanceAdjustmentRecordDao().queryAll().sortedBy { it.id }.map {
                BackupBalanceAdjustmentRecord(
                    it.id,
                    it.accountId,
                    it.delta,
                    it.occurredAt,
                    it.createdAt,
                    it.updatedAt,
                    it.deletedAt,
                    it.operationId,
                )
            },
            recurringReminders = database.recurringReminderDao().queryAll().sortedBy { it.id }.map {
                BackupRecurringReminder(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    accountId = it.accountId,
                    direction = it.direction,
                    amount = it.amount,
                    periodType = it.periodType,
                    periodValue = it.periodValue,
                    periodMonth = it.periodMonth,
                    isEnabled = it.isEnabled,
                    nextDueAt = it.nextDueAt,
                    anchorDueAt = it.anchorDueAt,
                    lastConfirmedAt = it.lastConfirmedAt,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            accountReminderConfigs = accounts.map { account ->
                val row = configs[account.id]
                val fallback = BalanceUpdateReminderConfig(isEnabled = account.closedAt == null)
                BackupAccountReminderConfig(
                    accountId = account.id,
                    config = BackupBalanceUpdateReminderConfig(
                        period = row?.period ?: fallback.period.value,
                        weekday = row?.weekday ?: fallback.weekday.value,
                        monthDay = row?.monthDay ?: fallback.monthDay,
                        hour = row?.hour ?: fallback.hour,
                        minute = row?.minute ?: fallback.minute,
                        isEnabled = row?.isEnabled ?: fallback.isEnabled,
                    ),
                )
            },
            savingsGoal = database.savingsGoalDao().query()?.let {
                BackupSavingsGoal(it.id, it.targetAmount, it.createdAt, it.updatedAt)
            },
        )
    }
}
