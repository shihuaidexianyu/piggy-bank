package com.shihuaidexianyu.money.data.repository

import android.content.Context
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    private val snapshotState = MutableStateFlow(loadSnapshot())

    val snapshot: StateFlow<MoneySnapshot> = snapshotState.asStateFlow()

    constructor(context: Context) : this(File(context.filesDir, "money_store.json"))

    fun update(transform: (MoneySnapshot) -> MoneySnapshot): MoneySnapshot {
        synchronized(this) {
            val updated = transform(snapshotState.value)
            persist(updated)
            snapshotState.value = updated
            return updated
        }
    }

    private fun loadSnapshot(): MoneySnapshot {
        if (!storageFile.exists()) return MoneySnapshot()
        return runCatching {
            val text = storageFile.readText(Charsets.UTF_8)
            if (text.isBlank()) MoneySnapshot() else parseSnapshot(JSONObject(text))
        }.getOrElse { MoneySnapshot() }
    }

    private fun persist(snapshot: MoneySnapshot) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(serializeSnapshot(snapshot).toString(), Charsets.UTF_8)
    }

    private fun serializeSnapshot(snapshot: MoneySnapshot): JSONObject {
        return JSONObject().apply {
            put("nextAccountId", snapshot.nextAccountId)
            put("nextCashFlowId", snapshot.nextCashFlowId)
            put("nextTransferId", snapshot.nextTransferId)
            put("nextBalanceUpdateId", snapshot.nextBalanceUpdateId)
            put("nextAdjustmentId", snapshot.nextAdjustmentId)
            put("changeVersion", snapshot.changeVersion)
            put("accounts", JSONArray().apply { snapshot.accounts.forEach { put(it.toJson()) } })
            put("cashFlowRecords", JSONArray().apply { snapshot.cashFlowRecords.forEach { put(it.toJson()) } })
            put("transferRecords", JSONArray().apply { snapshot.transferRecords.forEach { put(it.toJson()) } })
            put("balanceUpdates", JSONArray().apply { snapshot.balanceUpdates.forEach { put(it.toJson()) } })
            put("adjustments", JSONArray().apply { snapshot.adjustments.forEach { put(it.toJson()) } })
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
            archivedAt = item.optNullableLong("archivedAt"),
            isArchived = item.optBoolean("isArchived"),
            lastUsedAt = item.optNullableLong("lastUsedAt"),
            lastBalanceUpdateAt = item.optNullableLong("lastBalanceUpdateAt"),
            displayOrder = item.optInt("displayOrder"),
            colorName = item.optString("colorName", "blue"),
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

private fun Account.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("initialBalance", initialBalance)
    put("createdAt", createdAt)
    put("archivedAt", archivedAt ?: JSONObject.NULL)
    put("isArchived", isArchived)
    put("lastUsedAt", lastUsedAt ?: JSONObject.NULL)
    put("lastBalanceUpdateAt", lastBalanceUpdateAt ?: JSONObject.NULL)
    put("displayOrder", displayOrder)
    put("colorName", colorName)
}

private fun CashFlowRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("direction", direction)
    put("amount", amount)
    put("purpose", purpose)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
}

private fun TransferRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("fromAccountId", fromAccountId)
    put("toAccountId", toAccountId)
    put("amount", amount)
    put("note", note)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
}

private fun BalanceUpdateRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("actualBalance", actualBalance)
    put("systemBalanceBeforeUpdate", systemBalanceBeforeUpdate)
    put("delta", delta)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
}

private fun BalanceAdjustmentRecord.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("delta", delta)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
}
