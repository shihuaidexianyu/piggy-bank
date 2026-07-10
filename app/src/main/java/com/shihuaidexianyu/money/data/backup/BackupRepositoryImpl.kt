package com.shihuaidexianyu.money.data.backup

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.toEntity
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupCashFlowRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupPortableSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupRecurringReminder
import com.shihuaidexianyu.money.domain.model.backup.BackupSavingsGoal
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository

class BackupRepositoryImpl(
    private val database: MoneyDatabase,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) : BackupRepository {
    private val currentSnapshotReader = RoomBackupSnapshotReader(database)

    internal suspend fun replaceAll(snapshot: MoneyBackupSnapshot) {
        replaceAllInternal(snapshot, expectedCurrentContentSha256 = null)
    }

    override suspend fun replaceAllIfUnchanged(
        snapshot: MoneyBackupSnapshot,
        expectedCurrentContentSha256: String,
    ) {
        replaceAllInternal(snapshot, expectedCurrentContentSha256)
    }

    private suspend fun replaceAllInternal(
        snapshot: MoneyBackupSnapshot,
        expectedCurrentContentSha256: String?,
    ) {
        val normalized = BackupSnapshotNormalizer.normalizeForPersistence(snapshot)
        val targetContentSha256 = BackupContentHasher.sha256(normalized)
        database.withTransaction {
            if (expectedCurrentContentSha256 != null) {
                val current = currentSnapshotReader.readInCurrentTransaction(snapshot.metadata.exportedAt)
                require(BackupContentHasher.sha256(current) == expectedCurrentContentSha256) {
                    "生成安全快照后账本已发生变化，请重试导入"
                }
            }
            database.savingsGoalDao().deleteAll()
            database.recurringReminderDao().deleteAll()
            database.balanceAdjustmentRecordDao().deleteAll()
            database.balanceUpdateRecordDao().deleteAll()
            database.transferRecordDao().deleteAll()
            database.cashFlowRecordDao().deleteAll()
            database.accountDao().deleteAll()

            database.accountDao().insertAll(
                normalized.accounts.map { account -> account.toDomain().toEntity() },
            )
            database.cashFlowRecordDao().insertAll(normalized.cashFlowRecords.map { it.toDomain().toEntity() })
            database.transferRecordDao().insertAll(normalized.transferRecords.map { it.toDomain().toEntity() })
            database.balanceUpdateRecordDao().insertAll(normalized.balanceUpdateRecords.map { it.toDomain().toEntity() })
            database.balanceAdjustmentRecordDao().insertAll(
                normalized.balanceAdjustmentRecords.map { it.toDomain().toEntity() },
            )
            database.recurringReminderDao().insertAll(normalized.recurringReminders.map { it.toDomain().toEntity() })
            normalized.savingsGoal?.let { database.savingsGoalDao().insert(it.toDomain().toEntity()) }
            portableSettingsRepository.replace(normalized.portableSettings.toDomain())
            accountReminderSettingsRepository.replaceReminderConfigs(
                normalized.accountReminderConfigs.associate { row ->
                    row.accountId to row.config.toDomain().copy(lastNotifiedBoundaryAt = null)
                },
            )

            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                check(!cursor.moveToFirst()) { "导入后的外键检查失败" }
            }
            val persisted = currentSnapshotReader.readInCurrentTransaction(snapshot.metadata.exportedAt)
            check(BackupContentHasher.sha256(persisted) == targetContentSha256) {
                "导入结果复读校验失败"
            }
        }
    }
}

private fun BackupAccount.toDomain() = Account(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    isHidden = isHidden,
    closedAt = closedAt,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
    iconName = normalizeAccountIconName(iconName),
)

private fun BackupCashFlowRecord.toDomain() = CashFlowRecord(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BackupTransferRecord.toDomain() = TransferRecord(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BackupBalanceUpdateRecord.toDomain() = BalanceUpdateRecord(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BackupBalanceAdjustmentRecord.toDomain() = BalanceAdjustmentRecord(
    id = id,
    accountId = accountId,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BackupRecurringReminder.toDomain() = RecurringReminder(
    id = id,
    name = name,
    type = type,
    accountId = accountId,
    direction = direction,
    amount = amount,
    periodType = periodType,
    periodValue = periodValue,
    periodMonth = periodMonth,
    isEnabled = isEnabled,
    nextDueAt = nextDueAt,
    anchorDueAt = anchorDueAt,
    lastNotifiedDueAt = null,
    lastConfirmedAt = lastConfirmedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupPortableSettings.toDomain() = PortableSettings(
    currencySymbol = normalizeCurrencySymbol(currencySymbol),
    amountColorMode = AmountColorMode.fromValue(amountColorMode),
    monthlyBudgetAmount = monthlyBudgetAmount,
)

private fun BackupBalanceUpdateReminderConfig.toDomain() = BalanceUpdateReminderConfig(
    period = BalanceUpdateReminderPeriod.fromValue(period),
    weekday = BalanceUpdateReminderWeekday.fromValue(weekday),
    monthDay = monthDay,
    hour = hour,
    minute = minute,
    isEnabled = isEnabled,
    lastNotifiedBoundaryAt = null,
)

private fun BackupSavingsGoal.toDomain() = SavingsGoal(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
