package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.model.TransferRecord
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
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class BuildExportSnapshotUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val databaseVersion: Int,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(): MoneyBackupSnapshot = invoke(clockProvider.nowMillis())

    suspend operator fun invoke(exportedAt: Long): MoneyBackupSnapshot =
        transactionRepository.runInTransaction {
            val accounts = accountRepository.queryAllAccounts().sortedBy { it.id }
            MoneyBackupSnapshot(
                metadata = BackupMetadata(
                    schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
                    databaseVersion = databaseVersion,
                    exportedAt = exportedAt,
                ),
                portableSettings = portableSettingsRepository.query().toBackup(),
                accounts = accounts.map(Account::toBackup),
                cashFlowRecords = transactionRepository.queryAllCashFlowRecords()
                    .sortedBy { it.id }
                    .map(CashFlowRecord::toBackup),
                transferRecords = transactionRepository.queryAllTransferRecords()
                    .sortedBy { it.id }
                    .map(TransferRecord::toBackup),
                balanceUpdateRecords = transactionRepository.queryAllBalanceUpdateRecords()
                    .sortedBy { it.id }
                    .map(BalanceUpdateRecord::toBackup),
                balanceAdjustmentRecords = transactionRepository.queryAllBalanceAdjustmentRecords()
                    .sortedBy { it.id }
                    .map(BalanceAdjustmentRecord::toBackup),
                recurringReminders = recurringReminderRepository.queryAll()
                    .sortedBy { it.id }
                    .map(RecurringReminder::toBackup),
                accountReminderConfigs = accounts.map { account ->
                    BackupAccountReminderConfig(
                        accountId = account.id,
                        config = accountReminderSettingsRepository.getReminderConfig(account.id).toBackup(),
                    )
                },
                savingsGoal = savingsGoalRepository.query()?.toBackup(),
            )
        }
}

private fun Account.toBackup() = BackupAccount(
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
    iconName = iconName,
)

private fun CashFlowRecord.toBackup() = BackupCashFlowRecord(
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

private fun TransferRecord.toBackup() = BackupTransferRecord(
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

private fun BalanceUpdateRecord.toBackup() = BackupBalanceUpdateRecord(
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

private fun BalanceAdjustmentRecord.toBackup() = BackupBalanceAdjustmentRecord(
    id = id,
    accountId = accountId,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun RecurringReminder.toBackup() = BackupRecurringReminder(
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
    lastConfirmedAt = lastConfirmedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun PortableSettings.toBackup() = BackupPortableSettings(
    currencySymbol = currencySymbol,
    amountColorMode = amountColorMode.value,
    monthlyBudgetAmount = monthlyBudgetAmount,
)

private fun BalanceUpdateReminderConfig.toBackup() = BackupBalanceUpdateReminderConfig(
    period = period.value,
    weekday = weekday.value,
    monthDay = monthDay,
    hour = hour,
    minute = minute,
    isEnabled = isEnabled,
)

private fun SavingsGoal.toBackup() = BackupSavingsGoal(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
