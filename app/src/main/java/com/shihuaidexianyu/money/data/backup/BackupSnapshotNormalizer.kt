package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

/** Produces the exact portable state that [BackupRepositoryImpl] persists. */
object BackupSnapshotNormalizer {
    fun normalizeForPersistence(snapshot: MoneyBackupSnapshot): MoneyBackupSnapshot {
        val lastUsed = mutableMapOf<Long, Long>()
        val lastBalance = mutableMapOf<Long, Long>()
        fun mark(accountId: Long, occurredAt: Long) {
            lastUsed[accountId] = maxOf(lastUsed[accountId] ?: Long.MIN_VALUE, occurredAt)
        }
        snapshot.cashFlowRecords.filter { it.deletedAt == null }.forEach { mark(it.accountId, it.occurredAt) }
        snapshot.transferRecords.filter { it.deletedAt == null }.forEach {
            mark(it.fromAccountId, it.occurredAt)
            mark(it.toAccountId, it.occurredAt)
        }
        snapshot.balanceUpdateRecords.filter { it.deletedAt == null }.forEach {
            mark(it.accountId, it.occurredAt)
            lastBalance[it.accountId] = maxOf(lastBalance[it.accountId] ?: Long.MIN_VALUE, it.occurredAt)
        }
        snapshot.balanceAdjustmentRecords.filter { it.deletedAt == null }.forEach {
            mark(it.accountId, it.occurredAt)
        }
        val closedAccountIds = snapshot.accounts.filter { it.closedAt != null }.mapTo(mutableSetOf()) { it.id }
        return snapshot.copy(
            portableSettings = snapshot.portableSettings.copy(
                currencySymbol = normalizeCurrencySymbol(snapshot.portableSettings.currencySymbol),
            ),
            accounts = snapshot.accounts.sortedBy { it.id }.map { account ->
                account.copy(
                    lastUsedAt = maxOf(account.createdAt, lastUsed[account.id] ?: account.createdAt),
                    lastBalanceUpdateAt = lastBalance[account.id],
                )
            },
            cashFlowRecords = snapshot.cashFlowRecords.sortedBy { it.id },
            transferRecords = snapshot.transferRecords.sortedBy { it.id },
            balanceUpdateRecords = snapshot.balanceUpdateRecords.sortedBy { it.id },
            balanceAdjustmentRecords = snapshot.balanceAdjustmentRecords.sortedBy { it.id },
            recurringReminders = snapshot.recurringReminders.sortedBy { it.id }.map { reminder ->
                if (reminder.accountId in closedAccountIds) reminder.copy(isEnabled = false) else reminder
            },
            accountReminderConfigs = snapshot.accountReminderConfigs.sortedBy { it.accountId }.map { row ->
                if (row.accountId in closedAccountIds) {
                    row.copy(config = row.config.copy(isEnabled = false))
                } else {
                    row
                }
            },
        )
    }
}
