package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
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
import com.shihuaidexianyu.money.domain.model.backup.BackupRecurringReminder
import com.shihuaidexianyu.money.domain.model.backup.BackupSavingsGoal
import com.shihuaidexianyu.money.domain.model.backup.BackupSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class BuildExportSnapshotUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val databaseVersion: Int,
) {
    suspend operator fun invoke(exportedAt: Long = System.currentTimeMillis()): MoneyBackupSnapshot {
        val accounts = (accountRepository.queryActiveAccounts() + accountRepository.queryArchivedAccounts())
            .sortedBy { it.id }
        return MoneyBackupSnapshot(
            metadata = BackupMetadata(
                schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
                databaseVersion = databaseVersion,
                exportedAt = exportedAt,
            ),
            settings = settingsRepository.observeSettings().first().toBackup(),
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
            savingsGoals = savingsGoalRepository.queryAll()
                .sortedBy { it.id }
                .map(SavingsGoal::toBackup),
        )
    }
}

private fun Account.toBackup(): BackupAccount =
    BackupAccount(
        id = id,
        name = name,
        initialBalance = initialBalance,
        createdAt = createdAt,
        archivedAt = archivedAt,
        isArchived = isArchived,
        lastUsedAt = lastUsedAt,
        lastBalanceUpdateAt = lastBalanceUpdateAt,
        displayOrder = displayOrder,
        colorName = colorName,
        iconName = iconName,
    )

private fun CashFlowRecord.toBackup(): BackupCashFlowRecord =
    BackupCashFlowRecord(
        id = id,
        accountId = accountId,
        direction = direction,
        amount = amount,
        purpose = purpose,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )

private fun TransferRecord.toBackup(): BackupTransferRecord =
    BackupTransferRecord(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )

private fun BalanceUpdateRecord.toBackup(): BackupBalanceUpdateRecord =
    BackupBalanceUpdateRecord(
        id = id,
        accountId = accountId,
        actualBalance = actualBalance,
        systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

private fun BalanceAdjustmentRecord.toBackup(): BackupBalanceAdjustmentRecord =
    BackupBalanceAdjustmentRecord(
        id = id,
        accountId = accountId,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

private fun RecurringReminder.toBackup(): BackupRecurringReminder =
    BackupRecurringReminder(
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
        lastConfirmedAt = lastConfirmedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun AppSettings.toBackup(): BackupSettings =
    BackupSettings(
        homePeriod = homePeriod.value,
        currencySymbol = currencySymbol,
        showStaleMark = showStaleMark,
        themeMode = themeMode.value,
        amountColorMode = amountColorMode.value,
        lastHistoryKeyword = lastHistoryKeyword,
        lastHistoryExcludeKeyword = lastHistoryExcludeKeyword,
        lastHistoryAccountId = lastHistoryAccountId,
        lastHistoryDateStartAt = lastHistoryDateStartAt,
        lastHistoryDateEndAt = lastHistoryDateEndAt,
        lastHistoryMinAmountText = lastHistoryMinAmountText,
        lastHistoryMaxAmountText = lastHistoryMaxAmountText,
        lastHistoryAmountDirection = lastHistoryAmountDirection,
    )

private fun BalanceUpdateReminderConfig.toBackup(): BackupBalanceUpdateReminderConfig =
    BackupBalanceUpdateReminderConfig(
        period = period.value,
        weekday = weekday.value,
        monthDay = monthDay,
        hour = hour,
        minute = minute,
    )

private fun SavingsGoal.toBackup(): BackupSavingsGoal =
    BackupSavingsGoal(
        id = id,
        name = name,
        targetAmount = targetAmount,
        createdAt = createdAt,
        accountIds = accountIds,
    )
