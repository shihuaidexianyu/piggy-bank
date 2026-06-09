package com.shihuaidexianyu.money.domain.model.backup

import kotlinx.serialization.Serializable

const val MONEY_BACKUP_SCHEMA_VERSION = 1

@Serializable
data class MoneyBackupSnapshot(
    val metadata: BackupMetadata,
    val settings: BackupSettings,
    val accounts: List<BackupAccount>,
    val cashFlowRecords: List<BackupCashFlowRecord>,
    val transferRecords: List<BackupTransferRecord>,
    val balanceUpdateRecords: List<BackupBalanceUpdateRecord>,
    val balanceAdjustmentRecords: List<BackupBalanceAdjustmentRecord>,
    val recurringReminders: List<BackupRecurringReminder>,
    val accountReminderConfigs: List<BackupAccountReminderConfig>,
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
data class BackupSettings(
    val homePeriod: String,
    val currencySymbol: String,
    val showStaleMark: Boolean,
    val themeMode: String,
    val amountColorMode: String,
    val lastHistoryKeyword: String,
    val lastHistoryExcludeKeyword: String = "",
    val lastHistoryAccountId: Long,
    val lastHistoryDateStartAt: Long,
    val lastHistoryDateEndAt: Long,
    val lastHistoryMinAmountText: String,
    val lastHistoryMaxAmountText: String,
    val lastHistoryAmountDirection: String,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val archivedAt: Long?,
    val isArchived: Boolean,
    val lastUsedAt: Long?,
    val lastBalanceUpdateAt: Long?,
    val displayOrder: Int,
    val colorName: String,
)

@Serializable
data class BackupCashFlowRecord(
    val id: Long,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val purpose: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
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
    val isDeleted: Boolean,
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
)

@Serializable
data class BackupBalanceAdjustmentRecord(
    val id: Long,
    val accountId: Long,
    val delta: Long,
    val sourceUpdateRecordId: Long,
    val occurredAt: Long,
    val createdAt: Long,
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
    val weekday: String,
    val hour: Int,
    val minute: Int,
)
