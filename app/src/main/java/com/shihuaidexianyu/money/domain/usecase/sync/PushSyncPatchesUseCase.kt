package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationAction
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.sync.NotePatch
import com.shihuaidexianyu.money.domain.model.sync.PatchResult
import com.shihuaidexianyu.money.domain.model.sync.PushSyncBatchResult
import com.shihuaidexianyu.money.domain.model.sync.SYNC_ERROR_INVALID_PATCH
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorPayloads
import com.shihuaidexianyu.money.domain.model.sync.SyncPatchStatus
import com.shihuaidexianyu.money.domain.model.toAiLedgerSnapshot
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.AiMutationIdentity
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Applies a `sync.push` batch of note-only patches (spec 4.6 / 5.5).
 *
 * - Replay: the batch idempotency key is the envelope `requestId` (journal UNIQUE); a repeated
 *   request returns the stored per-patch results without touching the ledger.
 * - Validation is per patch and never fails the batch: only cash_flow/transfer, only the `note`
 *   field, trimmed, at most 200 chars (same limit as the UI's normalizeLedgerNote policy).
 * - Each applied patch goes through the existing update use cases (CAS on expectedUpdatedAt),
 *   so account guards, activity refresh and the sync change-log instrumentation all apply.
 * - A batch with at least one applied patch is journaled as one batch entry + items for LIFO
 *   batch undo. A batch that applied nothing (all conflict/invalid) is NOT journaled: the
 *   journal is the undo stack for successful mutations, and retrying such a batch is safely
 *   re-classified because conflict/invalid outcomes are stable (updatedAt is strictly
 *   monotonic, validation is stateless).
 */
class PushSyncPatchesUseCase(
    private val journalRepository: AiMutationJournalRepository,
    private val transactionRepository: TransactionRepository,
    private val syncRepository: SyncRepository,
    private val updateCashFlowRecordUseCase: UpdateCashFlowRecordUseCase,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val clockProvider: ClockProvider,
) {
    /** Stored results for a replayed batch requestId, or null when this requestId is new. */
    suspend fun findStoredResults(requestId: String): List<PatchResult>? =
        journalRepository.queryByRequestId(requestId)
            ?.takeIf { it.entryType == AiMutationEntryType.BATCH }
            ?.let { entry -> storedResults(entry) }

    suspend operator fun invoke(
        identity: AiMutationIdentity,
        expectedDatasetId: String,
        patches: List<NotePatch>,
    ): PushSyncBatchResult {
        require(patches.size <= MAX_PATCHES_PER_BATCH) { "单批补丁数量不能超过 $MAX_PATCHES_PER_BATCH 条" }
        validateIdentity(identity)
        return transactionRepository.runInTransaction {
            val dataset = syncRepository.readDatasetState()
            if (dataset.datasetId != expectedDatasetId) throw SyncDatasetMismatchException()
            journalRepository.queryByRequestId(identity.requestId)?.let { existing ->
                check(existing.entryType == AiMutationEntryType.BATCH) {
                    "请求标识已用于非批量同步操作"
                }
                return@runInTransaction PushSyncBatchResult(storedResults(existing), replayed = true)
            }

            val results = ArrayList<PatchResult>(patches.size)
            val items = ArrayList<AiMutationJournalItem>(patches.size)
            patches.forEachIndexed { index, patch ->
                val outcome = applyPatch(patch)
                results += outcome.result
                items += outcome.toItem(patch, index)
            }
            val appliedCount = results.count { it.status == SyncPatchStatus.APPLIED }
            val conflictCount = results.count { it.status == SyncPatchStatus.CONFLICT }
            // Only successful mutations enter the undo stack ("只有成功的数据修改才会入栈").
            // A zero-applied batch has nothing to undo; its retry re-classifies deterministically.
            if (appliedCount > 0) {
                val entry = AiMutationJournalEntry(
                    requestId = identity.requestId,
                    sessionId = identity.sessionId,
                    clientName = identity.clientName,
                    action = AiMutationAction.BATCH_NOTE_UPDATE,
                    recordKind = null,
                    recordId = null,
                    summary = "AI 批量修改 ${patches.size} 条备注（成功 $appliedCount，冲突 $conflictCount）",
                    beforeSnapshotJson = null,
                    afterSnapshotJson = null,
                    undoTokenJson = null,
                    createdAt = clockProvider.nowMillis(),
                    entryType = AiMutationEntryType.BATCH,
                    itemCount = patches.size,
                    appliedCount = appliedCount,
                    conflictCount = conflictCount,
                )
                journalRepository.insertBatch(entry, items)
            }
            PushSyncBatchResult(results, replayed = false)
        }
    }

    private suspend fun storedResults(entry: AiMutationJournalEntry): List<PatchResult> =
        journalRepository.queryItems(entry.id).map { item ->
            PatchResult(
                patchId = item.patchId,
                status = SyncPatchStatus.fromValue(item.status),
                revision = item.revision,
                serverUpdatedAt = item.serverUpdatedAt,
                serverPayloadJson = item.serverPayloadJson,
                errorCode = item.errorCode,
                errorMessage = item.errorMessage,
            )
        }

    private class PatchOutcome(
        val result: PatchResult,
        val beforeSnapshot: AiLedgerRecordSnapshot?,
        val afterSnapshot: AiLedgerRecordSnapshot?,
    ) {
        fun toItem(patch: NotePatch, index: Int): AiMutationJournalItem = AiMutationJournalItem(
            itemIndex = index,
            patchId = patch.patchId,
            // Raw wire value kept verbatim: invalid patches may carry unparseable kinds.
            entityKind = patch.entityKind,
            recordId = patch.recordId,
            status = result.status.value,
            beforeSnapshotJson = beforeSnapshot?.let { journalJson.encodeToString(it) },
            afterSnapshotJson = afterSnapshot?.let { journalJson.encodeToString(it) },
            expectedUpdatedAt = patch.expectedUpdatedAt,
            revision = result.revision,
            serverUpdatedAt = result.serverUpdatedAt,
            serverPayloadJson = result.serverPayloadJson,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
    }

    private suspend fun applyPatch(patch: NotePatch): PatchOutcome {
        val kind = SyncEntityKind.fromValue(patch.entityKind)
        if (kind == null) {
            return invalidOutcome(patch, "记录类型无效")
        }
        if (kind != SyncEntityKind.CASH_FLOW && kind != SyncEntityKind.TRANSFER) {
            return invalidOutcome(patch, "补丁只支持收支和转账记录")
        }
        if (patch.changes.keys != setOf(NOTE_FIELD)) {
            return invalidOutcome(patch, "补丁只能修改 note 字段")
        }
        val note = patch.changes.getValue(NOTE_FIELD).trim()
        if (note.length > MAX_SYNC_NOTE_LENGTH) {
            return invalidOutcome(patch, "备注长度不能超过 $MAX_SYNC_NOTE_LENGTH 字")
        }

        val current = queryStoredSnapshot(kind, patch.recordId)
            ?: return invalidOutcome(patch, "记录不存在或已删除")
        if (current.deletedAt != null || current.updatedAt != patch.expectedUpdatedAt) {
            return conflictOutcome(patch, current)
        }

        return try {
            when (kind) {
                SyncEntityKind.CASH_FLOW -> updateCashFlowRecordUseCase(
                    recordId = patch.recordId,
                    accountId = requireNotNull(current.accountId),
                    direction = CashFlowDirection.fromValue(requireNotNull(current.direction)),
                    amount = current.amount,
                    note = note,
                    occurredAt = current.occurredAt,
                    expectedUpdatedAt = current.updatedAt,
                )
                SyncEntityKind.TRANSFER -> updateTransferRecordUseCase(
                    recordId = patch.recordId,
                    fromAccountId = requireNotNull(current.fromAccountId),
                    toAccountId = requireNotNull(current.toAccountId),
                    amount = current.amount,
                    note = note,
                    occurredAt = current.occurredAt,
                    expectedUpdatedAt = current.updatedAt,
                )
                else -> error("unreachable")
            }
            PatchOutcome(
                result = PatchResult(
                    patchId = patch.patchId,
                    status = SyncPatchStatus.APPLIED,
                    revision = syncRepository.queryLatestRevisionFor(kind, patch.recordId),
                ),
                beforeSnapshot = current,
                afterSnapshot = requireNotNull(queryStoredSnapshot(kind, patch.recordId)),
            )
        } catch (error: LedgerRecordChangedException) {
            conflictOutcome(patch, queryStoredSnapshot(kind, patch.recordId))
        } catch (error: IllegalArgumentException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        } catch (error: IllegalStateException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        }
    }

    private suspend fun conflictOutcome(
        patch: NotePatch,
        current: AiLedgerRecordSnapshot?,
    ): PatchOutcome = PatchOutcome(
        result = PatchResult(
            patchId = patch.patchId,
            status = SyncPatchStatus.CONFLICT,
            serverUpdatedAt = current?.updatedAt,
            serverPayloadJson = current?.let {
                mirrorPayloadJson(requireNotNull(SyncEntityKind.fromValue(patch.entityKind)), patch.recordId)
            },
        ),
        beforeSnapshot = null,
        afterSnapshot = null,
    )

    private fun invalidOutcome(patch: NotePatch, message: String): PatchOutcome = PatchOutcome(
        result = PatchResult(
            patchId = patch.patchId,
            status = SyncPatchStatus.INVALID,
            errorCode = SYNC_ERROR_INVALID_PATCH,
            errorMessage = message,
        ),
        beforeSnapshot = null,
        afterSnapshot = null,
    )

    private suspend fun queryStoredSnapshot(kind: SyncEntityKind, recordId: Long): AiLedgerRecordSnapshot? =
        when (kind) {
            SyncEntityKind.CASH_FLOW ->
                transactionRepository.queryStoredCashFlowRecordById(recordId)?.toAiLedgerSnapshot()
            SyncEntityKind.TRANSFER ->
                transactionRepository.queryStoredTransferRecordById(recordId)?.toAiLedgerSnapshot()
            else -> null
        }

    private suspend fun mirrorPayloadJson(kind: SyncEntityKind, recordId: Long): String? = when (kind) {
        SyncEntityKind.CASH_FLOW -> transactionRepository.queryStoredCashFlowRecordById(recordId)
            ?.let(SyncMirrorPayloads::cashFlowPayloadJson)
        SyncEntityKind.TRANSFER -> transactionRepository.queryStoredTransferRecordById(recordId)
            ?.let(SyncMirrorPayloads::transferPayloadJson)
        else -> null
    }

    private fun validateIdentity(identity: AiMutationIdentity) {
        require(identity.requestId.isNotBlank() && identity.requestId.length <= 128) { "请求标识无效" }
        require(identity.sessionId.isNotBlank() && identity.sessionId.length <= 128) { "会话标识无效" }
        require(identity.clientName.isNotBlank() && identity.clientName.length <= 80) { "客户端名称无效" }
    }

    companion object {
        const val MAX_PATCHES_PER_BATCH = 50
        const val NOTE_FIELD = "note"
        /** Same trim + length policy as the UI's normalizeLedgerNote (ui/record/LedgerFormPolicy). */
        const val MAX_SYNC_NOTE_LENGTH = 200

        private val journalJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
