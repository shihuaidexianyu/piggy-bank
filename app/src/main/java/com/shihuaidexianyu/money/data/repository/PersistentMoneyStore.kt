package com.shihuaidexianyu.money.data.repository

import android.content.Context
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only loader for the legacy `money_store.json` file format. The write path was removed when
 * the app migrated to Room — this class exists only to support [com.shihuaidexianyu.money.data.db.LegacyMoneyStoreImporter].
 *
 * If the legacy file is corrupt, [loadSnapshot] returns an empty [MoneySnapshot] (the legacy data
 * is effectively unrecoverable; the user must restore from a manual backup). We log nothing here
 * to avoid noise on a path that should rarely fire.
 */
data class MoneySnapshot(
    val nextAccountId: Long = 1,
    val nextCashFlowId: Long = 1,
    val nextTransferId: Long = 1,
    val nextBalanceUpdateId: Long = 1,
    val nextAdjustmentId: Long = 1,
    val changeVersion: Long = 0,
    val accounts: List<Account> = emptyList(),
    val cashFlowRecords: List<CashFlowRecord> = emptyList(),
    val transferRecords: List<TransferRecord> = emptyList(),
    val balanceUpdates: List<BalanceUpdateRecord> = emptyList(),
    val adjustments: List<BalanceAdjustmentRecord> = emptyList(),
)

class PersistentMoneyStore(
    private val storageFile: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "money_store.json"))

    fun loadSnapshot(): MoneySnapshot {
        if (!storageFile.exists()) return MoneySnapshot()
        return runCatching {
            val text = storageFile.readText(Charsets.UTF_8)
            if (text.isBlank()) MoneySnapshot() else parseSnapshot(JSONObject(text))
        }.getOrElse { MoneySnapshot() }
    }

    private fun parseSnapshot(json: JSONObject): MoneySnapshot {
        return MoneySnapshot(
            nextAccountId = json.optLong("nextAccountId", 1),
            nextCashFlowId = json.optLong("nextCashFlowId", 1),
            nextTransferId = json.optLong("nextTransferId", 1),
            nextBalanceUpdateId = json.optLong("nextBalanceUpdateId", 1),
            nextAdjustmentId = json.optLong("nextAdjustmentId", 1),
            changeVersion = json.optLong("changeVersion", 0),
            accounts = json.optJSONArray("accounts").toAccountList(),
            cashFlowRecords = json.optJSONArray("cashFlowRecords").toCashFlowList(),
            transferRecords = json.optJSONArray("transferRecords").toTransferList(),
            balanceUpdates = json.optJSONArray("balanceUpdates").toBalanceUpdateList(),
            adjustments = json.optJSONArray("adjustments").toAdjustmentList(),
        )
    }
}

private fun JSONArray?.toAccountList(): List<Account> =
    this.toObjectList { item ->
        Account(
            id = item.getLong("id"),
            name = item.getString("name"),
            initialBalance = item.getLong("initialBalance"),
            createdAt = item.getLong("createdAt"),
            archivedAt = item.optNullableLong("archivedAt"),
            isArchived = item.optBoolean("isArchived"),
            lastUsedAt = item.optNullableLong("lastUsedAt"),
            lastBalanceUpdateAt = item.optNullableLong("lastBalanceUpdateAt"),
            displayOrder = item.optInt("displayOrder"),
            colorName = item.optString("colorName", "blue"),
            iconName = item.optString("iconName", "wallet"),
        )
    }

private fun JSONArray?.toCashFlowList(): List<CashFlowRecord> =
    this.toObjectList { item ->
        CashFlowRecord(
            id = item.getLong("id"),
            accountId = item.getLong("accountId"),
            direction = item.getString("direction"),
            amount = item.getLong("amount"),
            purpose = item.getString("purpose"),
            occurredAt = item.getLong("occurredAt"),
            createdAt = item.getLong("createdAt"),
            updatedAt = item.getLong("updatedAt"),
            isDeleted = item.optBoolean("isDeleted"),
        )
    }

private fun JSONArray?.toTransferList(): List<TransferRecord> =
    this.toObjectList { item ->
        TransferRecord(
            id = item.getLong("id"),
            fromAccountId = item.getLong("fromAccountId"),
            toAccountId = item.getLong("toAccountId"),
            amount = item.getLong("amount"),
            note = item.getString("note"),
            occurredAt = item.getLong("occurredAt"),
            createdAt = item.getLong("createdAt"),
            updatedAt = item.getLong("updatedAt"),
            isDeleted = item.optBoolean("isDeleted"),
        )
    }

private fun JSONArray?.toBalanceUpdateList(): List<BalanceUpdateRecord> =
    this.toObjectList { item ->
        BalanceUpdateRecord(
            id = item.getLong("id"),
            accountId = item.getLong("accountId"),
            actualBalance = item.getLong("actualBalance"),
            systemBalanceBeforeUpdate = item.getLong("systemBalanceBeforeUpdate"),
            delta = item.getLong("delta"),
            occurredAt = item.getLong("occurredAt"),
            createdAt = item.getLong("createdAt"),
        )
    }

private fun JSONArray?.toAdjustmentList(): List<BalanceAdjustmentRecord> =
    this.toObjectList { item ->
        BalanceAdjustmentRecord(
            id = item.getLong("id"),
            accountId = item.getLong("accountId"),
            delta = item.getLong("delta"),
            occurredAt = item.getLong("occurredAt"),
            createdAt = item.getLong("createdAt"),
        )
    }

private inline fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            add(mapper(getJSONObject(index)))
        }
    }
}

private fun JSONObject.optNullableLong(key: String): Long? {
    return if (has(key) && !isNull(key)) getLong(key) else null
}
