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
    /** Batch note-only patches submitted through `sync.push` (entryType = batch). */
    BATCH_NOTE_UPDATE,
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

enum class AiMutationEntryType(val value: String) {
    SINGLE("single"),
    BATCH("batch"),
    ;

    companion object {
        fun fromValue(value: String?): AiMutationEntryType =
            entries.firstOrNull { it.value == value } ?: SINGLE
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

fun CashFlowRecord.toAiLedgerSnapshot() = AiLedgerRecordSnapshot(
    kind = LedgerRecordKind.CASH_FLOW,
    recordId = id,
    operationId = operationId,
    accountId = accountId,
    direction = direction,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun TransferRecord.toAiLedgerSnapshot() = AiLedgerRecordSnapshot(
    kind = LedgerRecordKind.TRANSFER,
    recordId = id,
    operationId = operationId,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/** Semantic equality for undo conflict checks: every field except the `updatedAt` version token. */
fun AiLedgerRecordSnapshot.semanticallyEquals(other: AiLedgerRecordSnapshot): Boolean =
    copy(updatedAt = 0L) == other.copy(updatedAt = 0L)

data class AiMutationJournalEntry(
    val id: Long = 0,
    val requestId: String,
    val sessionId: String,
    val clientName: String,
    val action: AiMutationAction,
    /** Null for batch entries; batches address their records through [AiMutationJournalItem]. */
    val recordKind: LedgerRecordKind?,
    val recordId: Long?,
    val summary: String,
    val beforeSnapshotJson: String?,
    /** Null for batch entries; per-item snapshots live in [AiMutationJournalItem]. */
    val afterSnapshotJson: String?,
    val undoTokenJson: String?,
    val status: AiMutationJournalStatus = AiMutationJournalStatus.APPLIED,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val undoRequestId: String? = null,
    val entryType: AiMutationEntryType = AiMutationEntryType.SINGLE,
    /** Batch metadata (null for single entries): patch count, applied count, conflict count. */
    val itemCount: Int? = null,
    val appliedCount: Int? = null,
    val conflictCount: Int? = null,
)

/**
 * One patch inside a batch journal entry. Applied items keep before/after snapshots and the
 * client's expectedUpdatedAt so the whole batch can be conflict-checked and undone as a unit.
 * Conflict items keep the server state they reported so an idempotent replay returns the
 * original per-patch results byte-for-byte.
 */
data class AiMutationJournalItem(
    val journalId: Long = 0,
    val itemIndex: Int,
    val patchId: String,
    /** Raw wire entityKind (may be unparseable for invalid patches). */
    val entityKind: String,
    val recordId: Long,
    /** SyncPatchStatus wire value: applied / conflict / invalid. */
    val status: String,
    val beforeSnapshotJson: String? = null,
    val afterSnapshotJson: String? = null,
    val expectedUpdatedAt: Long,
    /** Change-log revision allocated for applied items. */
    val revision: Long? = null,
    /** Server state reported with conflict results. */
    val serverUpdatedAt: Long? = null,
    val serverPayloadJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    /** Set when the whole batch entry is undone (items have no individual lifecycle). */
    val undoneAt: Long? = null,
)

data class AiMutationReceipt(
    val entry: AiMutationJournalEntry,
    val replayed: Boolean,
)

/** Per-record outcome of a batch undo attempt (restored, or the conflict that blocked it). */
data class AiUndoBatchItem(
    val recordKind: LedgerRecordKind,
    val recordId: Long,
    val restored: Boolean,
    val message: String? = null,
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

    /** A batch popped from the stack top and fully restored. */
    data class BatchUndone(
        val entry: AiMutationJournalEntry,
        val items: List<AiUndoBatchItem>,
        val replayed: Boolean,
    ) : UndoLatestAiMutationResult

    /** A batch whose pre-check found drift; nothing was restored (all-or-nothing). */
    data class BatchConflict(
        val entry: AiMutationJournalEntry,
        val message: String,
        val items: List<AiUndoBatchItem>,
    ) : UndoLatestAiMutationResult
}
