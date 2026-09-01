package com.shihuaidexianyu.money.domain.model.sync

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mirror-row payload DTOs (spec 4.3) and builders from domain records.
 *
 * Wire conventions (fixture-pinned): amounts are decimal strings in minor units; timestamps are
 * epoch-millis Longs; enums are lowercase snake-case; null fields are omitted from the JSON.
 */
@Serializable
data class SyncAccountPayload(
    val accountId: Long,
    val name: String,
    val initialBalance: String,
    val kind: String,
    val isHidden: Boolean,
    val closedAt: Long? = null,
    val displayOrder: Int,
    val colorName: String,
    val iconName: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val lastBalanceUpdateAt: Long? = null,
)

@Serializable
data class SyncCashFlowPayload(
    val recordId: Long,
    val accountId: Long,
    val direction: String,
    val amount: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class SyncTransferPayload(
    val recordId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class SyncBalanceUpdatePayload(
    val recordId: Long,
    val accountId: Long,
    val actualBalance: String,
    val systemBalanceBeforeUpdate: String,
    val delta: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class SyncBalanceAdjustmentPayload(
    val recordId: Long,
    val accountId: Long,
    val delta: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

/** JSON for mirror payloads: null keys omitted, defaults always encoded (fixture convention). */
val SyncMirrorJson: Json = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    encodeDefaults = true
}

object SyncMirrorPayloads {
    fun accountPayload(account: Account): SyncAccountPayload = SyncAccountPayload(
        accountId = account.id,
        name = account.name,
        initialBalance = account.initialBalance.toString(),
        kind = account.kind.value,
        isHidden = account.isHidden,
        closedAt = account.closedAt,
        displayOrder = account.displayOrder,
        colorName = account.colorName,
        iconName = account.iconName,
        createdAt = account.createdAt,
        lastUsedAt = account.lastUsedAt,
        lastBalanceUpdateAt = account.lastBalanceUpdateAt,
    )

    fun cashFlowPayload(record: CashFlowRecord): SyncCashFlowPayload = SyncCashFlowPayload(
        recordId = record.id,
        accountId = record.accountId,
        direction = record.direction,
        amount = record.amount.toString(),
        note = record.note,
        occurredAt = record.occurredAt,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        deletedAt = record.deletedAt,
        operationId = record.operationId,
    )

    fun transferPayload(record: TransferRecord): SyncTransferPayload = SyncTransferPayload(
        recordId = record.id,
        fromAccountId = record.fromAccountId,
        toAccountId = record.toAccountId,
        amount = record.amount.toString(),
        note = record.note,
        occurredAt = record.occurredAt,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        deletedAt = record.deletedAt,
        operationId = record.operationId,
    )

    fun balanceUpdatePayload(record: BalanceUpdateRecord): SyncBalanceUpdatePayload =
        SyncBalanceUpdatePayload(
            recordId = record.id,
            accountId = record.accountId,
            actualBalance = record.actualBalance.toString(),
            systemBalanceBeforeUpdate = record.systemBalanceBeforeUpdate.toString(),
            delta = record.delta.toString(),
            occurredAt = record.occurredAt,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            deletedAt = record.deletedAt,
            operationId = record.operationId,
        )

    fun balanceAdjustmentPayload(record: BalanceAdjustmentRecord): SyncBalanceAdjustmentPayload =
        SyncBalanceAdjustmentPayload(
            recordId = record.id,
            accountId = record.accountId,
            delta = record.delta.toString(),
            occurredAt = record.occurredAt,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            deletedAt = record.deletedAt,
            operationId = record.operationId,
        )

    fun accountPayloadJson(account: Account): String =
        SyncMirrorJson.encodeToString(SyncAccountPayload.serializer(), accountPayload(account))

    fun cashFlowPayloadJson(record: CashFlowRecord): String =
        SyncMirrorJson.encodeToString(SyncCashFlowPayload.serializer(), cashFlowPayload(record))

    fun transferPayloadJson(record: TransferRecord): String =
        SyncMirrorJson.encodeToString(SyncTransferPayload.serializer(), transferPayload(record))

    fun balanceUpdatePayloadJson(record: BalanceUpdateRecord): String =
        SyncMirrorJson.encodeToString(SyncBalanceUpdatePayload.serializer(), balanceUpdatePayload(record))

    fun balanceAdjustmentPayloadJson(record: BalanceAdjustmentRecord): String =
        SyncMirrorJson.encodeToString(
            SyncBalanceAdjustmentPayload.serializer(),
            balanceAdjustmentPayload(record),
        )
}
