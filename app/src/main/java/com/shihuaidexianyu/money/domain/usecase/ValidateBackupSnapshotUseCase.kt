package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ACCOUNT_ICON_NAMES
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

data class BackupValidationResult(
    val accountCount: Int,
    val cashFlowCount: Int,
    val transferCount: Int,
    val balanceUpdateCount: Int,
    val balanceAdjustmentCount: Int,
    val reminderCount: Int,
    val exportedAt: Long,
)

class ValidateBackupSnapshotUseCase {
    operator fun invoke(snapshot: MoneyBackupSnapshot): BackupValidationResult {
        require(snapshot.metadata.schemaVersion == MONEY_BACKUP_SCHEMA_VERSION) {
            "不支持的备份版本：${snapshot.metadata.schemaVersion}"
        }
        requirePositive(snapshot.metadata.exportedAt, "metadata.exportedAt")
        requireKnown(snapshot.settings.homePeriod, HomePeriod.entries.map { it.value }, "settings.homePeriod")
        requireKnown(snapshot.settings.themeMode, ThemeMode.entries.map { it.value }, "settings.themeMode")
        requireKnown(
            snapshot.settings.amountColorMode,
            AmountColorMode.entries.map { it.value },
            "settings.amountColorMode",
        )

        val accountIds = snapshot.accounts.map { account ->
            requirePositive(account.id, "accounts.id")
            requirePositive(account.createdAt, "accounts.createdAt")
            requireNullablePositive(account.archivedAt, "accounts.archivedAt")
            requireNullablePositive(account.lastUsedAt, "accounts.lastUsedAt")
            requireNullablePositive(account.lastBalanceUpdateAt, "accounts.lastBalanceUpdateAt")
            requireKnown(account.iconName, ACCOUNT_ICON_NAMES, "accounts.iconName")
            account.id
        }
        requireNoDuplicates(accountIds, "accounts.id")
        val accountIdSet = accountIds.toSet()

        snapshot.cashFlowRecords.forEach { record ->
            requireReference(record.accountId, accountIdSet, "cashFlowRecords.accountId")
            requireKnown(record.direction, CashFlowDirection.entries.map { it.value }, "cashFlowRecords.direction")
            requirePositive(record.amount, "cashFlowRecords.amount")
            requirePositive(record.occurredAt, "cashFlowRecords.occurredAt")
            requirePositive(record.createdAt, "cashFlowRecords.createdAt")
            requirePositive(record.updatedAt, "cashFlowRecords.updatedAt")
        }

        snapshot.transferRecords.forEach { record ->
            requireReference(record.fromAccountId, accountIdSet, "transferRecords.fromAccountId")
            requireReference(record.toAccountId, accountIdSet, "transferRecords.toAccountId")
            requirePositive(record.amount, "transferRecords.amount")
            requirePositive(record.occurredAt, "transferRecords.occurredAt")
            requirePositive(record.createdAt, "transferRecords.createdAt")
            requirePositive(record.updatedAt, "transferRecords.updatedAt")
        }

        snapshot.balanceUpdateRecords.forEach { record ->
            requireReference(record.accountId, accountIdSet, "balanceUpdateRecords.accountId")
            requirePositive(record.occurredAt, "balanceUpdateRecords.occurredAt")
            requirePositive(record.createdAt, "balanceUpdateRecords.createdAt")
        }

        snapshot.balanceAdjustmentRecords.forEach { record ->
            requireReference(record.accountId, accountIdSet, "balanceAdjustmentRecords.accountId")
            requireNonZero(record.delta, "balanceAdjustmentRecords.delta")
            requirePositive(record.occurredAt, "balanceAdjustmentRecords.occurredAt")
            requirePositive(record.createdAt, "balanceAdjustmentRecords.createdAt")
        }

        snapshot.recurringReminders.forEach { reminder ->
            requireReference(reminder.accountId, accountIdSet, "recurringReminders.accountId")
            requireKnown(reminder.type, ReminderType.entries.map { it.value }, "recurringReminders.type")
            requireKnown(reminder.direction, CashFlowDirection.entries.map { it.value }, "recurringReminders.direction")
            requireKnown(reminder.periodType, ReminderPeriodType.entries.map { it.value }, "recurringReminders.periodType")
            requirePositive(reminder.amount, "recurringReminders.amount")
            requirePositive(reminder.nextDueAt, "recurringReminders.nextDueAt")
            requireNullablePositive(reminder.lastConfirmedAt, "recurringReminders.lastConfirmedAt")
            requirePositive(reminder.createdAt, "recurringReminders.createdAt")
            requirePositive(reminder.updatedAt, "recurringReminders.updatedAt")
        }

        val configAccountIds = snapshot.accountReminderConfigs.map { config ->
            requireReference(config.accountId, accountIdSet, "accountReminderConfigs.accountId")
            requireKnown(
                config.config.period,
                BalanceUpdateReminderPeriod.entries.map { it.value },
                "accountReminderConfigs.config.period",
            )
            requireKnown(
                config.config.weekday,
                BalanceUpdateReminderWeekday.entries.map { it.value },
                "accountReminderConfigs.config.weekday",
            )
            require(config.config.monthDay in 1..31) { "accountReminderConfigs.config.monthDay 超出范围" }
            require(config.config.hour in 0..23) { "accountReminderConfigs.config.hour 超出范围" }
            require(config.config.minute in 0..59) { "accountReminderConfigs.config.minute 超出范围" }
            config.accountId
        }
        requireNoDuplicates(configAccountIds, "accountReminderConfigs.accountId")

        return BackupValidationResult(
            accountCount = snapshot.accounts.size,
            cashFlowCount = snapshot.cashFlowRecords.size,
            transferCount = snapshot.transferRecords.size,
            balanceUpdateCount = snapshot.balanceUpdateRecords.size,
            balanceAdjustmentCount = snapshot.balanceAdjustmentRecords.size,
            reminderCount = snapshot.recurringReminders.size,
            exportedAt = snapshot.metadata.exportedAt,
        )
    }

    private fun requireReference(id: Long, validIds: Set<Long>, fieldName: String) {
        require(id in validIds) { "$fieldName 引用不存在：$id" }
    }

    private fun requireNoDuplicates(values: List<Long>, fieldName: String) {
        val duplicate = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        require(duplicate == null) { "$fieldName 重复：$duplicate" }
    }

    private fun requireKnown(value: String, allowedValues: List<String>, fieldName: String) {
        require(value in allowedValues) { "$fieldName 不支持：$value" }
    }

    private fun requireNonNegative(value: Long, fieldName: String) {
        require(value >= 0L) { "$fieldName 不能为负数" }
    }

    private fun requireNonZero(value: Long, fieldName: String) {
        require(value != 0L) { "$fieldName 不能为 0" }
    }

    private fun requirePositive(value: Long, fieldName: String) {
        require(value > 0L) { "$fieldName 必须大于 0" }
    }

    private fun requireNullablePositive(value: Long?, fieldName: String) {
        if (value != null) requirePositive(value, fieldName)
    }
}
