package com.shihuaidexianyu.money.data.backup

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.toEntity
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupCashFlowRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupRecurringReminder
import com.shihuaidexianyu.money.domain.model.backup.BackupSavingsGoal
import com.shihuaidexianyu.money.domain.model.backup.BackupSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository

class BackupRepositoryImpl(
    private val database: MoneyDatabase,
    private val settingsRepository: SettingsRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) : BackupRepository {
    override suspend fun replaceAll(snapshot: MoneyBackupSnapshot) {
        database.withTransaction {
            database.savingsGoalDao().deleteAll()
            database.recurringReminderDao().deleteAll()
            database.balanceAdjustmentRecordDao().deleteAll()
            database.balanceUpdateRecordDao().deleteAll()
            database.transferRecordDao().deleteAll()
            database.cashFlowRecordDao().deleteAll()
            database.accountDao().deleteAll()

            database.accountDao().insertAll(snapshot.accounts.map { it.toDomain().toEntity() })
            database.cashFlowRecordDao().insertAll(snapshot.cashFlowRecords.map { it.toDomain().toEntity() })
            database.transferRecordDao().insertAll(snapshot.transferRecords.map { it.toDomain().toEntity() })
            database.balanceUpdateRecordDao().insertAll(snapshot.balanceUpdateRecords.map { it.toDomain().toEntity() })
            database.balanceAdjustmentRecordDao().insertAll(
                snapshot.balanceAdjustmentRecords.map { it.toDomain().toEntity() },
            )
            database.recurringReminderDao().insertAll(snapshot.recurringReminders.map { it.toDomain().toEntity() })

            snapshot.savingsGoals.forEach { backupGoal ->
                val goal = backupGoal.toDomain()
                database.savingsGoalDao().insert(goal.toEntity())
            }
        }

        settingsRepository.replaceSettings(snapshot.settings.toDomain())
        accountReminderSettingsRepository.replaceReminderConfigs(
            snapshot.accountReminderConfigs.associate { config ->
                config.accountId to config.config.toDomain()
            },
        )
    }
}

private fun BackupAccount.toDomain(): Account =
    Account(
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
        iconName = normalizeAccountIconName(iconName),
    )

private fun BackupCashFlowRecord.toDomain(): CashFlowRecord =
    CashFlowRecord(
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

private fun BackupTransferRecord.toDomain(): TransferRecord =
    TransferRecord(
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

private fun BackupBalanceUpdateRecord.toDomain(): BalanceUpdateRecord =
    BalanceUpdateRecord(
        id = id,
        accountId = accountId,
        actualBalance = actualBalance,
        systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

private fun BackupBalanceAdjustmentRecord.toDomain(): BalanceAdjustmentRecord =
    BalanceAdjustmentRecord(
        id = id,
        accountId = accountId,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

private fun BackupRecurringReminder.toDomain(): RecurringReminder =
    RecurringReminder(
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

private fun BackupSettings.toDomain(): AppSettings =
    AppSettings(
        homePeriod = HomePeriod.fromValue(homePeriod),
        currencySymbol = normalizeCurrencySymbol(currencySymbol),
        showStaleMark = showStaleMark,
        themeMode = ThemeMode.fromValue(themeMode),
        amountColorMode = AmountColorMode.fromValue(amountColorMode),
        lastHistoryKeyword = lastHistoryKeyword,
        lastHistoryExcludeKeyword = lastHistoryExcludeKeyword,
        lastHistoryAccountId = lastHistoryAccountId,
        lastHistoryDateStartAt = lastHistoryDateStartAt,
        lastHistoryDateEndAt = lastHistoryDateEndAt,
        lastHistoryMinAmountText = lastHistoryMinAmountText,
        lastHistoryMaxAmountText = lastHistoryMaxAmountText,
        lastHistoryAmountDirection = lastHistoryAmountDirection,
    )

private fun BackupBalanceUpdateReminderConfig.toDomain(): BalanceUpdateReminderConfig =
    BalanceUpdateReminderConfig(
        period = BalanceUpdateReminderPeriod.fromValue(period),
        weekday = BalanceUpdateReminderWeekday.fromValue(weekday),
        monthDay = monthDay,
        hour = hour,
        minute = minute,
    )

private fun BackupSavingsGoal.toDomain(): SavingsGoal =
    SavingsGoal(
        id = id,
        targetAmount = targetAmount,
        createdAt = createdAt,
    )
