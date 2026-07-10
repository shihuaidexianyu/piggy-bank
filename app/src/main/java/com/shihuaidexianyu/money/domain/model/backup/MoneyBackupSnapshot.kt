package com.shihuaidexianyu.money.domain.model.backup

import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_MONTH_DAY
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_PERIOD
import kotlinx.serialization.Serializable

const val MONEY_BACKUP_SCHEMA_VERSION = 4

/**
 * Portable, user-controlled data only. Device preferences, notification de-duplication state and
 * local migration state deliberately do not belong in this format.
 */
@Serializable
data class MoneyBackupSnapshot(
    val metadata: BackupMetadata,
    val portableSettings: BackupPortableSettings,
    val accounts: List<BackupAccount>,
    val cashFlowRecords: List<BackupCashFlowRecord>,
    val transferRecords: List<BackupTransferRecord>,
    val balanceUpdateRecords: List<BackupBalanceUpdateRecord>,
    val balanceAdjustmentRecords: List<BackupBalanceAdjustmentRecord>,
    val recurringReminders: List<BackupRecurringReminder>,
    val accountReminderConfigs: List<BackupAccountReminderConfig>,
    val savingsGoal: BackupSavingsGoal? = null,
)

@Serializable
data class BackupMetadata(
    val schemaVersion: Int,
    val databaseVersion: Int,
    val exportedAt: Long,
    val appVersionName: String? = null,
    val appVersionCode: Int? = null,
)

@Serializable
data class BackupPortableSettings(
    val currencySymbol: String,
    val amountColorMode: String,
    val monthlyBudgetAmount: Long? = null,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val isHidden: Boolean = false,
    val closedAt: Long? = null,
    val lastUsedAt: Long?,
    val lastBalanceUpdateAt: Long?,
    val displayOrder: Int,
    val colorName: String,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
)

@Serializable
data class BackupCashFlowRecord(
    val id: Long,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class BackupTransferRecord(
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class BackupBalanceUpdateRecord(
    val id: Long,
    val accountId: Long,
    val actualBalance: Long,
    val systemBalanceBeforeUpdate: Long,
    val delta: Long,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class BackupBalanceAdjustmentRecord(
    val id: Long,
    val accountId: Long,
    val delta: Long,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class BackupRecurringReminder(
    val id: Long,
    val name: String,
    val type: String,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val periodType: String,
    val periodValue: Int,
    val periodMonth: Int?,
    val isEnabled: Boolean,
    val nextDueAt: Long,
    val anchorDueAt: Long,
    val lastConfirmedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupAccountReminderConfig(
    val accountId: Long,
    val config: BackupBalanceUpdateReminderConfig,
)

@Serializable
data class BackupBalanceUpdateReminderConfig(
    val period: String = DEFAULT_BALANCE_UPDATE_REMINDER_PERIOD,
    val weekday: String,
    val monthDay: Int = DEFAULT_BALANCE_UPDATE_REMINDER_MONTH_DAY,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
)

@Serializable
data class BackupSavingsGoal(
    val id: Long = 1L,
    val targetAmount: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
