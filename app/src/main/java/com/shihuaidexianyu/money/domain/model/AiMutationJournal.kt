package com.shihuaidexianyu.money.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiMutationAction {
    CREATE_CASH_FLOW,
    UPDATE_CASH_FLOW,
    DELETE_CASH_FLOW,
    CREATE_TRANSFER,
    UPDATE_TRANSFER,
    DELETE_TRANSFER,
}
enum class AiMutationJournalStatus(val value: String) {
    APPLIED("applied"),
    UNDONE("undone"),
    DISCARDED("discarded"),
    ;

    companion object {
        fun fromValue(value: String): AiMutationJournalStatus =
            entries.firstOrNull { it.value == value } ?: error("Unknown AI journal status: $value")
    }
}

/**
 * Stable, serializable ledger state used for journal conflict checks and inverse operations.
 * [updatedAt] is retained for diagnostics but deliberately ignored by semantic conflict checks:
 * undoing a newer stack entry creates a fresh revision while restoring the older semantic state.
 */
@Serializable
data class AiLedgerRecordSnapshot(
    val kind: LedgerRecordKind,
    val recordId: Long,
    val operationId: String,
    val accountId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val direction: String? = null,
    val amount: Long,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class AiMutationJournalEntry(
    val id: Long = 0,
    val requestId: String,
    val sessionId: String,
    val clientName: String,
    val action: AiMutationAction,
    val recordKind: LedgerRecordKind,
    val recordId: Long,
    val summary: String,
    val beforeSnapshotJson: String?,
    val afterSnapshotJson: String,
    val undoTokenJson: String?,
    val status: AiMutationJournalStatus = AiMutationJournalStatus.APPLIED,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val undoRequestId: String? = null,
)

data class AiMutationReceipt(
    val entry: AiMutationJournalEntry,
    val replayed: Boolean,
)

sealed interface UndoLatestAiMutationResult {
    data class Undone(
        val entry: AiMutationJournalEntry,
        val replayed: Boolean,
    ) : UndoLatestAiMutationResult

    data object Empty : UndoLatestAiMutationResult

    data class Conflict(
        val entry: AiMutationJournalEntry,
        val message: String,
    ) : UndoLatestAiMutationResult
}
