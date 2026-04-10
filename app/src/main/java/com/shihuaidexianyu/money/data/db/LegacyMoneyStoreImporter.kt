package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore

object LegacyMoneyStoreImporter {
    suspend fun importIfNeeded(
        context: Context,
        database: MoneyDatabase,
    ) {
        val hasDatabaseData = database.accountDao().queryActiveAccounts().isNotEmpty() ||
            database.accountDao().queryArchivedAccounts().isNotEmpty()
        if (hasDatabaseData) return

        val legacyStore = PersistentMoneyStore(context)
        val snapshot = legacyStore.snapshot.value
        val hasLegacyData = snapshot.accounts.isNotEmpty() ||
            snapshot.cashFlowRecords.isNotEmpty() ||
            snapshot.transferRecords.isNotEmpty() ||
            snapshot.balanceUpdates.isNotEmpty() ||
            snapshot.adjustments.isNotEmpty()
        if (!hasLegacyData) return

        database.withTransaction {
            snapshot.accounts.sortedBy { it.id }.forEach { database.accountDao().insert(it) }
            snapshot.cashFlowRecords.sortedBy { it.id }.forEach { database.cashFlowRecordDao().insert(it) }
            snapshot.transferRecords.sortedBy { it.id }.forEach { database.transferRecordDao().insert(it) }
            snapshot.balanceUpdates.sortedBy { it.id }.forEach { database.balanceUpdateRecordDao().insert(it) }
            snapshot.adjustments.sortedBy { it.id }.forEach { database.balanceAdjustmentRecordDao().insert(it) }
        }
    }
}

