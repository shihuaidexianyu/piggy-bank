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
 * Strict read-only loader for the legacy `money_store.json` format. Corruption is represented
 * explicitly so startup can stop on a recoverable error instead of silently treating data as empty.
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
) {
    val hasLedgerData: Boolean
        get() = accounts.isNotEmpty() ||
            cashFlowRecords.isNotEmpty() ||
            transferRecords.isNotEmpty() ||
            balanceUpdates.isNotEmpty() ||
            adjustments.isNotEmpty()
}

sealed interface LegacyMoneyStoreReadResult {
    data object Missing : LegacyMoneyStoreReadResult
    data object Empty : LegacyMoneyStoreReadResult
    data class Data(val snapshot: MoneySnapshot) : LegacyMoneyStoreReadResult
    data class Corrupt(val diagnostic: String) : LegacyMoneyStoreReadResult
}

class PersistentMoneyStore(
    private val storageFile: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "money_store.json"))

    fun readStrict(): LegacyMoneyStoreReadResult {
        if (!storageFile.exists()) return LegacyMoneyStoreReadResult.Missing
        val text = runCatching { storageFile.readText(Charsets.UTF_8) }
            .getOrElse { error ->
                return LegacyMoneyStoreReadResult.Corrupt(error.message ?: "无法读取旧账本文件")
            }
        if (text.isBlank()) return LegacyMoneyStoreReadResult.Empty
        return try {
            val snapshot = parseSnapshot(JSONObject(text))
            if (snapshot.hasLedgerData) {
                LegacyMoneyStoreReadResult.Data(snapshot)
            } else {
                LegacyMoneyStoreReadResult.Empty
            }
        } catch (error: Throwable) {
            LegacyMoneyStoreReadResult.Corrupt(error.message ?: "旧账本 JSON 已损坏")
        }
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
            closedAt = if (item.optBoolean("isArchived")) {
                item.optNullableLong("archivedAt") ?: item.getLong("createdAt")
            } else {
                null
            },
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
            note = item.getString("purpose"),
            occurredAt = item.getLong("occurredAt"),
            createdAt = item.getLong("createdAt"),
            updatedAt = item.getLong("updatedAt"),
            deletedAt = item.getLong("updatedAt").coerceAtLeast(item.getLong("createdAt"))
                .takeIf { item.optBoolean("isDeleted") },
            operationId = "cash:legacy-store:${item.getLong("id")}",
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
            deletedAt = item.getLong("updatedAt").coerceAtLeast(item.getLong("createdAt"))
                .takeIf { item.optBoolean("isDeleted") },
            operationId = "transfer:legacy-store:${item.getLong("id")}",
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
            updatedAt = item.getLong("createdAt"),
            operationId = "balance-update:legacy-store:${item.getLong("id")}",
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
            updatedAt = item.getLong("createdAt"),
            operationId = "balance-adjustment:legacy-store:${item.getLong("id")}",
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
