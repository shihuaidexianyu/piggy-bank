package com.shihuaidexianyu.money.domain.model

import kotlinx.serialization.Serializable

data class LedgerInsertResult(
    val recordId: Long,
    val inserted: Boolean,
)

@Serializable
enum class LedgerRecordKind {
    CASH_FLOW,
    TRANSFER,
    BALANCE_UPDATE,
    BALANCE_ADJUSTMENT,
}

@Serializable
data class LedgerUndoToken(
    val version: Int = 1,
    val kind: LedgerRecordKind,
    val recordId: Long,
    val operationId: String,
    val deletedAt: Long,
)

enum class RestoreLedgerResult {
    RESTORED,
    ALREADY_ACTIVE,
    STALE,
    NOT_FOUND,
}

class LedgerOperationConflictException(
    val kind: LedgerRecordKind,
    val operationId: String,
    val existingRecordId: Long,
) : IllegalStateException(
    "同一操作标识已用于不同内容的账本记录",
)

class LedgerRecordChangedException(
    val kind: LedgerRecordKind,
    val recordId: Long,
) : IllegalStateException(
    "记录已被修改或删除，请刷新后重试",
)
