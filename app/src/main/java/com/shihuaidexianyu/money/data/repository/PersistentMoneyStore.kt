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
    internal val storageFile: File,
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
            val snapshot = parseSnapshot(JSONObject(text)).also(::validateSnapshot)
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
        val cashFlowRecords = json.requireArray("cashFlowRecords").toCashFlowList()
        val transferRecords = json.requireArray("transferRecords").toTransferList()
        val balanceUpdates = json.requireArray("balanceUpdates").toBalanceUpdateList()
        val adjustments = json.requireArray("adjustments").toAdjustmentList()
        val latestEventAtByAccount = buildLatestEventAtByAccount(
            cashFlowRecords = cashFlowRecords,
            transferRecords = transferRecords,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
        )
        return MoneySnapshot(
            nextAccountId = json.optLong("nextAccountId", 1),
            nextCashFlowId = json.optLong("nextCashFlowId", 1),
            nextTransferId = json.optLong("nextTransferId", 1),
            nextBalanceUpdateId = json.optLong("nextBalanceUpdateId", 1),
            nextAdjustmentId = json.optLong("nextAdjustmentId", 1),
            changeVersion = json.optLong("changeVersion", 0),
            accounts = json.requireArray("accounts").toAccountList(latestEventAtByAccount),
            cashFlowRecords = cashFlowRecords,
            transferRecords = transferRecords,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
        )
    }
}

private fun JSONArray.toAccountList(latestEventAtByAccount: Map<Long, Long>): List<Account> =
    this.toObjectList { item ->
        val id = item.getLong("id")
        val createdAt = item.getLong("createdAt")
        Account(
            id = id,
            name = item.getString("name"),
            initialBalance = item.getLong("initialBalance"),
            createdAt = createdAt,
            closedAt = if (item.optBoolean("isArchived")) {
                listOfNotNull(
                    createdAt,
                    item.optNullableLong("archivedAt"),
                    item.optNullableLong("lastUsedAt"),
                    item.optNullableLong("lastBalanceUpdateAt"),
                    latestEventAtByAccount[id],
                ).max()
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

private fun JSONArray.toCashFlowList(): List<CashFlowRecord> =
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

private fun JSONArray.toTransferList(): List<TransferRecord> =
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

private fun JSONArray.toBalanceUpdateList(): List<BalanceUpdateRecord> =
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

private fun JSONArray.toAdjustmentList(): List<BalanceAdjustmentRecord> =
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

private inline fun <T> JSONArray.toObjectList(mapper: (JSONObject) -> T): List<T> {
    return buildList(length()) {
        for (index in 0 until length()) {
            add(mapper(getJSONObject(index)))
        }
    }
}

private fun JSONObject.requireArray(key: String): JSONArray {
    check(has(key) && !isNull(key)) { "旧账本缺少必要数组：$key" }
    return opt(key) as? JSONArray ?: error("旧账本字段 $key 必须是数组")
}

private fun buildLatestEventAtByAccount(
    cashFlowRecords: List<CashFlowRecord>,
    transferRecords: List<TransferRecord>,
    balanceUpdates: List<BalanceUpdateRecord>,
    adjustments: List<BalanceAdjustmentRecord>,
): Map<Long, Long> = buildMap {
    fun record(accountId: Long, occurredAt: Long) {
        put(accountId, maxOf(get(accountId) ?: Long.MIN_VALUE, occurredAt))
    }
    cashFlowRecords.forEach { record(it.accountId, it.occurredAt) }
    transferRecords.forEach {
        record(it.fromAccountId, it.occurredAt)
        record(it.toAccountId, it.occurredAt)
    }
    balanceUpdates.forEach { record(it.accountId, it.occurredAt) }
    adjustments.forEach { record(it.accountId, it.occurredAt) }
}

private fun validateSnapshot(snapshot: MoneySnapshot) {
    requireUniquePositiveIds("账户", snapshot.accounts.map { it.id })
    requireUniquePositiveIds("现金流水", snapshot.cashFlowRecords.map { it.id })
    requireUniquePositiveIds("转账", snapshot.transferRecords.map { it.id })
    requireUniquePositiveIds("核对记录", snapshot.balanceUpdates.map { it.id })
    requireUniquePositiveIds("余额调整", snapshot.adjustments.map { it.id })
    val accountIds = snapshot.accounts.mapTo(mutableSetOf()) { it.id }
    snapshot.cashFlowRecords.forEach { require(it.accountId in accountIds) { "现金流水引用了不存在的账户" } }
    snapshot.transferRecords.forEach {
        require(it.fromAccountId in accountIds && it.toAccountId in accountIds) { "转账引用了不存在的账户" }
        require(it.fromAccountId != it.toAccountId) { "转账不能在同一账户内发生" }
    }
    snapshot.balanceUpdates.forEach { require(it.accountId in accountIds) { "核对记录引用了不存在的账户" } }
    snapshot.adjustments.forEach { require(it.accountId in accountIds) { "余额调整引用了不存在的账户" } }
}

private fun requireUniquePositiveIds(label: String, ids: List<Long>) {
    require(ids.all { it > 0L }) { "$label ID 必须大于 0" }
    require(ids.distinct().size == ids.size) { "${label}包含重复 ID" }
}

private fun JSONObject.optNullableLong(key: String): Long? {
    return if (has(key) && !isNull(key)) getLong(key) else null
}
