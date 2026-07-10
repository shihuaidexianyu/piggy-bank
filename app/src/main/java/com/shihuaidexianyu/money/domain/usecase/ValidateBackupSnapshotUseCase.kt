package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ACCOUNT_ICON_NAMES
import com.shihuaidexianyu.money.domain.model.ACCOUNT_COLOR_NAMES
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.MAX_CURRENCY_SYMBOL_LENGTH
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.TimeMath
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.math.BigInteger

const val MAX_BACKUP_LEDGER_RECORDS = 200_000

data class BackupValidationResult(
    val accountCount: Int,
    val cashFlowCount: Int,
    val transferCount: Int,
    val balanceUpdateCount: Int,
    val balanceAdjustmentCount: Int,
    val reminderCount: Int,
    val savingsGoalCount: Int,
    val exportedAt: Long,
) {
    val ledgerRecordCount: Int
        get() = cashFlowCount + transferCount + balanceUpdateCount + balanceAdjustmentCount
}

class ValidateBackupSnapshotUseCase(
    private val clockProvider: ClockProvider,
) {
    operator fun invoke(snapshot: MoneyBackupSnapshot): BackupValidationResult {
        require(snapshot.metadata.schemaVersion == MONEY_BACKUP_SCHEMA_VERSION) {
            "metadata.schemaVersion 不支持：${snapshot.metadata.schemaVersion}"
        }
        require(snapshot.metadata.databaseVersion > 0) { "metadata.databaseVersion 必须大于 0" }
        requirePositive(snapshot.metadata.exportedAt, "metadata.exportedAt")
        require(snapshot.metadata.exportedAt <= clockProvider.nowMillis()) {
            "metadata.exportedAt 晚于当前时间"
        }
        require(snapshot.portableSettings.currencySymbol.isNotBlank()) { "portableSettings.currencySymbol 不能为空" }
        require(snapshot.portableSettings.currencySymbol.length <= MAX_CURRENCY_SYMBOL_LENGTH) {
            "portableSettings.currencySymbol 过长"
        }
        requireKnown(
            snapshot.portableSettings.amountColorMode,
            AmountColorMode.entries.map { it.value },
            "portableSettings.amountColorMode",
        )
        snapshot.portableSettings.monthlyBudgetAmount?.let {
            requirePositive(it, "portableSettings.monthlyBudgetAmount")
        }

        val ledgerCount = Math.addExact(
            Math.addExact(snapshot.cashFlowRecords.size, snapshot.transferRecords.size),
            Math.addExact(snapshot.balanceUpdateRecords.size, snapshot.balanceAdjustmentRecords.size),
        )
        require(ledgerCount <= MAX_BACKUP_LEDGER_RECORDS) {
            "ledgerRecords 数量超过 $MAX_BACKUP_LEDGER_RECORDS"
        }

        val accountsById = snapshot.accounts.associateByChecked("accounts") { account ->
            requirePositive(account.id, "accounts[${account.id}].id")
            require(account.name.isNotBlank()) { "accounts[${account.id}].name 不能为空" }
            requirePositive(account.createdAt, "accounts[${account.id}].createdAt")
            requireNotFuture(account.createdAt, snapshot.metadata.exportedAt, "accounts[${account.id}].createdAt")
            requireNullablePositive(account.closedAt, "accounts[${account.id}].closedAt")
            require(account.closedAt == null || account.closedAt >= account.createdAt) {
                "accounts[${account.id}].closedAt 早于开户时间"
            }
            requireNullablePositive(account.lastUsedAt, "accounts[${account.id}].lastUsedAt")
            requireNullablePositive(account.lastBalanceUpdateAt, "accounts[${account.id}].lastBalanceUpdateAt")
            require(account.displayOrder >= 0) { "accounts[${account.id}].displayOrder 不能为负数" }
            account.closedAt?.let { requireNotFuture(it, snapshot.metadata.exportedAt, "accounts[${account.id}].closedAt") }
            account.lastUsedAt?.let { requireNotFuture(it, snapshot.metadata.exportedAt, "accounts[${account.id}].lastUsedAt") }
            account.lastBalanceUpdateAt?.let {
                requireNotFuture(it, snapshot.metadata.exportedAt, "accounts[${account.id}].lastBalanceUpdateAt")
            }
            account.lastUsedAt?.let {
                require(it >= TimeMath.floorToMinute(account.createdAt)) {
                    "accounts[${account.id}].lastUsedAt 早于开户时间"
                }
                require(account.closedAt == null || it <= account.closedAt) {
                    "accounts[${account.id}].lastUsedAt 晚于关闭时间"
                }
            }
            account.lastBalanceUpdateAt?.let {
                require(it >= TimeMath.floorToMinute(account.createdAt)) {
                    "accounts[${account.id}].lastBalanceUpdateAt 早于开户时间"
                }
                require(account.closedAt == null || it <= account.closedAt) {
                    "accounts[${account.id}].lastBalanceUpdateAt 晚于关闭时间"
                }
            }
            requireKnown(account.iconName, ACCOUNT_ICON_NAMES, "accounts[${account.id}].iconName")
            requireKnown(account.colorName, ACCOUNT_COLOR_NAMES, "accounts[${account.id}].colorName")
            account.id
        }

        snapshot.cashFlowRecords.associateByChecked("cashFlowRecords") { record ->
            val path = "cashFlowRecords[${record.id}]"
            requireLedgerIdentity(record.id, record.operationId, path)
            val account = requireAccount(record.accountId, accountsById, "$path.accountId")
            requireKnown(record.direction, CashFlowDirection.entries.map { it.value }, "$path.direction")
            requirePositive(record.amount, "$path.amount")
            requireRecordTimes(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, path)
            requireRecordNotFuture(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, snapshot.metadata.exportedAt, path)
            requireWithinLifecycle(record.occurredAt, account, path)
            record.id
        }
        requireUniqueOperationIds(snapshot.cashFlowRecords.map { it.operationId }, "cashFlowRecords.operationId")

        snapshot.transferRecords.associateByChecked("transferRecords") { record ->
            val path = "transferRecords[${record.id}]"
            requireLedgerIdentity(record.id, record.operationId, path)
            require(record.fromAccountId != record.toAccountId) { "$path 不能自转账" }
            val from = requireAccount(record.fromAccountId, accountsById, "$path.fromAccountId")
            val to = requireAccount(record.toAccountId, accountsById, "$path.toAccountId")
            requirePositive(record.amount, "$path.amount")
            requireRecordTimes(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, path)
            requireRecordNotFuture(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, snapshot.metadata.exportedAt, path)
            requireWithinLifecycle(record.occurredAt, from, "$path.fromAccountId")
            requireWithinLifecycle(record.occurredAt, to, "$path.toAccountId")
            record.id
        }
        requireUniqueOperationIds(snapshot.transferRecords.map { it.operationId }, "transferRecords.operationId")

        snapshot.balanceUpdateRecords.associateByChecked("balanceUpdateRecords") { record ->
            val path = "balanceUpdateRecords[${record.id}]"
            requireLedgerIdentity(record.id, record.operationId, path)
            val account = requireAccount(record.accountId, accountsById, "$path.accountId")
            requireRecordTimes(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, path)
            requireRecordNotFuture(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, snapshot.metadata.exportedAt, path)
            requireWithinLifecycle(record.occurredAt, account, path)
            val evidenceDelta = checkedSubtract(record.actualBalance, record.systemBalanceBeforeUpdate, "$path.delta")
            require(evidenceDelta == record.delta) { "$path.delta 与核对证据不一致" }
            record.id
        }
        requireUniqueOperationIds(
            snapshot.balanceUpdateRecords.map { it.operationId },
            "balanceUpdateRecords.operationId",
        )

        snapshot.balanceAdjustmentRecords.associateByChecked("balanceAdjustmentRecords") { record ->
            val path = "balanceAdjustmentRecords[${record.id}]"
            requireLedgerIdentity(record.id, record.operationId, path)
            val account = requireAccount(record.accountId, accountsById, "$path.accountId")
            require(record.delta != 0L) { "$path.delta 不能为 0" }
            requireRecordTimes(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, path)
            requireRecordNotFuture(record.occurredAt, record.createdAt, record.updatedAt, record.deletedAt, snapshot.metadata.exportedAt, path)
            requireWithinLifecycle(record.occurredAt, account, path)
            record.id
        }
        requireUniqueOperationIds(
            snapshot.balanceAdjustmentRecords.map { it.operationId },
            "balanceAdjustmentRecords.operationId",
        )

        validateAggregateArithmetic(snapshot, accountsById)
        validateReminders(snapshot, accountsById)
        validateReminderConfigs(snapshot, accountsById)
        snapshot.savingsGoal?.let { goal ->
            require(goal.id == 1L) { "savingsGoal.id 必须为 1" }
            requirePositive(goal.targetAmount, "savingsGoal.targetAmount")
            requirePositive(goal.createdAt, "savingsGoal.createdAt")
            require(goal.updatedAt >= goal.createdAt) { "savingsGoal.updatedAt 早于 createdAt" }
            requireNotFuture(goal.updatedAt, snapshot.metadata.exportedAt, "savingsGoal.updatedAt")
        }

        return BackupValidationResult(
            accountCount = snapshot.accounts.size,
            cashFlowCount = snapshot.cashFlowRecords.size,
            transferCount = snapshot.transferRecords.size,
            balanceUpdateCount = snapshot.balanceUpdateRecords.size,
            balanceAdjustmentCount = snapshot.balanceAdjustmentRecords.size,
            reminderCount = snapshot.recurringReminders.size,
            savingsGoalCount = if (snapshot.savingsGoal == null) 0 else 1,
            exportedAt = snapshot.metadata.exportedAt,
        )
    }

    private fun validateReminders(snapshot: MoneyBackupSnapshot, accounts: Map<Long, BackupAccount>) {
        snapshot.recurringReminders.associateByChecked("recurringReminders") { reminder ->
            val path = "recurringReminders[${reminder.id}]"
            requirePositive(reminder.id, "$path.id")
            require(reminder.name.isNotBlank()) { "$path.name 不能为空" }
            requireAccount(reminder.accountId, accounts, "$path.accountId")
            require(!accounts.getValue(reminder.accountId).isClosed || !reminder.isEnabled) {
                "$path 已关闭账户的提醒不能启用"
            }
            requireKnown(reminder.type, ReminderType.entries.map { it.value }, "$path.type")
            requireKnown(reminder.direction, CashFlowDirection.entries.map { it.value }, "$path.direction")
            requireKnown(reminder.periodType, ReminderPeriodType.entries.map { it.value }, "$path.periodType")
            requirePositive(reminder.amount, "$path.amount")
            requirePositive(reminder.anchorDueAt, "$path.anchorDueAt")
            requirePositive(reminder.nextDueAt, "$path.nextDueAt")
            require(reminder.nextDueAt >= reminder.anchorDueAt) { "$path.nextDueAt 早于 anchorDueAt" }
            requireNullablePositive(reminder.lastConfirmedAt, "$path.lastConfirmedAt")
            requirePositive(reminder.createdAt, "$path.createdAt")
            require(reminder.updatedAt >= reminder.createdAt) { "$path.updatedAt 早于 createdAt" }
            requireNotFuture(reminder.updatedAt, snapshot.metadata.exportedAt, "$path.updatedAt")
            reminder.lastConfirmedAt?.let { requireNotFuture(it, snapshot.metadata.exportedAt, "$path.lastConfirmedAt") }
            val periodType = ReminderPeriodType.entries.single { it.value == reminder.periodType }
            ReminderScheduleValidator.validate(periodType, reminder.periodValue, reminder.periodMonth)
            when (periodType) {
                ReminderPeriodType.MONTHLY -> {
                    require(reminder.periodMonth == null) { "$path.periodMonth 月度提醒必须为空" }
                }
                ReminderPeriodType.YEARLY -> {
                    Unit
                }
                ReminderPeriodType.CUSTOM_DAYS -> {
                    require(reminder.periodMonth == null) { "$path.periodMonth 自定义天数提醒必须为空" }
                }
            }
            reminder.id
        }
    }

    private fun validateReminderConfigs(snapshot: MoneyBackupSnapshot, accounts: Map<Long, BackupAccount>) {
        val configMap = snapshot.accountReminderConfigs.associateByChecked("accountReminderConfigs") { row ->
            val path = "accountReminderConfigs[${row.accountId}]"
            requireAccount(row.accountId, accounts, "$path.accountId")
            require(!accounts.getValue(row.accountId).isClosed || !row.config.isEnabled) {
                "$path 已关闭账户的核对提醒不能启用"
            }
            requireKnown(row.config.period, BalanceUpdateReminderPeriod.entries.map { it.value }, "$path.period")
            requireKnown(row.config.weekday, BalanceUpdateReminderWeekday.entries.map { it.value }, "$path.weekday")
            require(row.config.monthDay in 1..31) { "$path.monthDay 超出范围" }
            require(row.config.hour in 0..23) { "$path.hour 超出范围" }
            require(row.config.minute in 0..59) { "$path.minute 超出范围" }
            row.accountId
        }
        require(configMap.keys == accounts.keys) {
            "accountReminderConfigs.accountId 必须与 accounts.id 完全一致"
        }
    }

    private fun validateAggregateArithmetic(snapshot: MoneyBackupSnapshot, accounts: Map<Long, BackupAccount>) {
        val events = ArrayList<TimedLedgerDelta>(
            snapshot.cashFlowRecords.size +
                snapshot.transferRecords.size * 2 +
                snapshot.balanceUpdateRecords.size +
                snapshot.balanceAdjustmentRecords.size,
        )
        snapshot.cashFlowRecords.filter { it.deletedAt == null }.forEach { record ->
            val delta = if (record.direction == CashFlowDirection.INFLOW.value) {
                record.amount
            } else {
                checkedNegate(record.amount, "cashFlowRecords[${record.id}].amount")
            }
            events += TimedLedgerDelta(record.accountId, record.occurredAt, delta)
        }
        snapshot.transferRecords.filter { it.deletedAt == null }.forEach { record ->
            events += TimedLedgerDelta(
                record.fromAccountId,
                record.occurredAt,
                checkedNegate(record.amount, "transferRecords[${record.id}].amount"),
            )
            events += TimedLedgerDelta(record.toAccountId, record.occurredAt, record.amount)
        }
        snapshot.balanceUpdateRecords.filter { it.deletedAt == null }.forEach {
            events += TimedLedgerDelta(it.accountId, it.occurredAt, it.delta)
        }
        snapshot.balanceAdjustmentRecords.filter { it.deletedAt == null }.forEach {
            events += TimedLedgerDelta(it.accountId, it.occurredAt, it.delta)
        }

        val eventsByAccount = events.groupBy(TimedLedgerDelta::accountId)
        val finalBalances = accounts.mapValues { (accountId, account) ->
            var balance = BigInteger.valueOf(account.initialBalance)
            val ordered = eventsByAccount[accountId].orEmpty().sortedBy(TimedLedgerDelta::occurredAt)
            var index = 0
            while (index < ordered.size) {
                val occurredAt = ordered[index].occurredAt
                var groupDelta = BigInteger.ZERO
                while (index < ordered.size && ordered[index].occurredAt == occurredAt) {
                    groupDelta = groupDelta.add(BigInteger.valueOf(ordered[index].delta))
                    index++
                }
                balance = balance.add(groupDelta)
                require(balance in LONG_RANGE) {
                    "accounts[$accountId] 在 $occurredAt 的历史余额算术溢出"
                }
            }
            balance.toLong()
        }
        finalBalances.values.fold(0L) { total, value -> checkedAdd(total, value, "accounts.totalBalance") }
    }

    private fun requireRecordTimes(
        occurredAt: Long,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
        path: String,
    ) {
        requirePositive(occurredAt, "$path.occurredAt")
        requirePositive(createdAt, "$path.createdAt")
        require(createdAt >= occurredAt) { "$path.createdAt 早于 occurredAt" }
        require(updatedAt >= createdAt) { "$path.updatedAt 早于 createdAt" }
        if (deletedAt != null) {
            require(deletedAt >= createdAt) { "$path.deletedAt 早于 createdAt" }
            require(updatedAt >= deletedAt) { "$path.updatedAt 早于 deletedAt" }
        }
    }

    private fun requireWithinLifecycle(occurredAt: Long, account: BackupAccount, path: String) {
        require(occurredAt >= TimeMath.floorToMinute(account.createdAt)) { "$path.occurredAt 早于账户开户时间" }
        require(account.closedAt == null || occurredAt <= account.closedAt) { "$path.occurredAt 晚于账户关闭时间" }
    }

    private fun requireLedgerIdentity(id: Long, operationId: String, path: String) {
        requirePositive(id, "$path.id")
        require(operationId.isNotBlank()) { "$path.operationId 不能为空" }
    }

    private fun requireAccount(id: Long, accounts: Map<Long, BackupAccount>, path: String): BackupAccount =
        requireNotNull(accounts[id]) { "$path 引用不存在：$id" }

    private fun requireUniqueOperationIds(values: List<String>, path: String) {
        val duplicate = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        require(duplicate == null) { "$path 重复：$duplicate" }
    }

    private fun <T> Iterable<T>.associateByChecked(path: String, key: (T) -> Long): Map<Long, T> {
        val result = linkedMapOf<Long, T>()
        forEach { item ->
            val id = key(item)
            require(result.put(id, item) == null) { "$path.id 重复：$id" }
        }
        return result
    }

    private fun checkedAdd(left: Long, right: Long, path: String): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$path 算术溢出")
    }

    private fun checkedSubtract(left: Long, right: Long, path: String): Long = try {
        Math.subtractExact(left, right)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$path 算术溢出")
    }

    private fun checkedNegate(value: Long, path: String): Long = try {
        Math.negateExact(value)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$path 算术溢出")
    }

    private fun requireKnown(value: String, allowed: List<String>, path: String) {
        require(value in allowed) { "$path 不支持：$value" }
    }

    private fun requirePositive(value: Long, path: String) {
        require(value > 0L) { "$path 必须大于 0" }
    }

    private fun requireNullablePositive(value: Long?, path: String) {
        if (value != null) requirePositive(value, path)
    }

    private fun requireRecordNotFuture(
        occurredAt: Long,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
        exportedAt: Long,
        path: String,
    ) {
        requireNotFuture(occurredAt, exportedAt, "$path.occurredAt")
        requireNotFuture(createdAt, exportedAt, "$path.createdAt")
        requireNotFuture(updatedAt, exportedAt, "$path.updatedAt")
        deletedAt?.let { requireNotFuture(it, exportedAt, "$path.deletedAt") }
    }

    private fun requireNotFuture(value: Long, exportedAt: Long, path: String) {
        require(value <= exportedAt) { "$path 晚于 metadata.exportedAt" }
    }

    private val BackupAccount.isClosed: Boolean
        get() = closedAt != null
}

private data class TimedLedgerDelta(
    val accountId: Long,
    val occurredAt: Long,
    val delta: Long,
)

private val LONG_RANGE = BigInteger.valueOf(Long.MIN_VALUE)..BigInteger.valueOf(Long.MAX_VALUE)
