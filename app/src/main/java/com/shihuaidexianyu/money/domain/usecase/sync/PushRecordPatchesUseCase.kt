package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationAction
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.sync.PatchResult
import com.shihuaidexianyu.money.domain.model.sync.PushSyncBatchResult
import com.shihuaidexianyu.money.domain.model.sync.RecordPatch
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
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Applies a `sync.push` batch of record patches (sync.push.records.v1): mixed
 * create/update/delete on cash_flow and transfer records.
 *
 * Same batch contract as [PushSyncPatchesUseCase]: envelope `requestId` is the idempotency key,
 * validation is per patch and never fails the batch, every mutation goes through the existing
 * leaf use cases (account guards, activity refresh, change-log instrumentation), and a batch is
 * journaled as one batch entry only when at least one patch applied — zero-applied batches are
 * re-classified deterministically on retry and stay out of the undo stack.
 *
 * Per-patch operationId is `ai:<requestId>:<patchId>` so creates inside one batch stay unique.
 * Create results return the new record id; undo of the batch is three-way per item: undo a
 * create by deleting, undo an update by restoring the before snapshot, undo a delete by
 * restoring the soft-deleted record (the undo token is rebuilt from the item snapshots).
 */
class PushRecordPatchesUseCase(
    private val journalRepository: AiMutationJournalRepository,
    private val transactionRepository: TransactionRepository,
    private val syncRepository: SyncRepository,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val updateCashFlowRecordUseCase: UpdateCashFlowRecordUseCase,
    private val deleteCashFlowRecordUseCase: DeleteCashFlowRecordUseCase,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
    private val clockProvider: ClockProvider,
) {
    /** Stored results for a replayed batch requestId, or null when this requestId is new. */
    suspend fun findStoredResults(requestId: String): List<PatchResult>? =
        journalRepository.queryByRequestId(requestId)
            ?.takeIf { it.entryType == AiMutationEntryType.BATCH && it.action == AiMutationAction.BATCH_RECORD_WRITE }
            ?.let { entry -> storedResults(entry) }

    suspend operator fun invoke(
        identity: AiMutationIdentity,
        expectedDatasetId: String,
        patches: List<RecordPatch>,
    ): PushSyncBatchResult {
        require(patches.size <= MAX_PATCHES_PER_BATCH) { "单批补丁数量不能超过 $MAX_PATCHES_PER_BATCH 条" }
        require(identity.requestId.isNotBlank() && identity.requestId.length <= 128) { "请求标识无效" }
        require(identity.sessionId.isNotBlank() && identity.sessionId.length <= 128) { "会话标识无效" }
        require(identity.clientName.isNotBlank() && identity.clientName.length <= 80) { "客户端名称无效" }
        return transactionRepository.runInTransaction {
            val dataset = syncRepository.readDatasetState()
            if (dataset.datasetId != expectedDatasetId) throw SyncDatasetMismatchException()
            journalRepository.queryByRequestId(identity.requestId)?.let { existing ->
                check(existing.entryType == AiMutationEntryType.BATCH) { "请求标识已用于非批量同步操作" }
                check(existing.action == AiMutationAction.BATCH_RECORD_WRITE) { "请求标识已用于其他类型的批量操作" }
                return@runInTransaction PushSyncBatchResult(storedResults(existing), replayed = true)
            }

            val seenPatchIds = HashSet<String>(patches.size)
            val results = ArrayList<PatchResult>(patches.size)
            val items = ArrayList<AiMutationJournalItem>(patches.size)
            patches.forEachIndexed { index, patch ->
                val outcome = if (seenPatchIds.add(patch.patchId)) {
                    applyPatch(identity, patch)
                } else {
                    invalidOutcome(patch, "patchId 重复")
                }
                results += outcome.result
                items += outcome.toItem(patch, index)
            }
            val appliedCount = results.count { it.status == SyncPatchStatus.APPLIED }
            val conflictCount = results.count { it.status == SyncPatchStatus.CONFLICT }
            if (appliedCount > 0) {
                val entry = AiMutationJournalEntry(
                    requestId = identity.requestId,
                    sessionId = identity.sessionId,
                    clientName = identity.clientName,
                    action = AiMutationAction.BATCH_RECORD_WRITE,
                    recordKind = null,
                    recordId = null,
                    summary = "AI 批量写入 ${patches.size} 条账目（成功 $appliedCount，冲突 $conflictCount）",
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
                recordId = item.recordId.takeIf { it > 0L },
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
        fun toItem(patch: RecordPatch, index: Int): AiMutationJournalItem = AiMutationJournalItem(
            itemIndex = index,
            patchId = patch.patchId,
            // Raw wire value kept verbatim: invalid patches may carry unparseable kinds.
            entityKind = patch.entityKind,
            // Creates learn their id only when applied; 0 marks "no record" for the NOT NULL column.
            recordId = result.recordId ?: patch.recordId ?: 0L,
            status = result.status.value,
            beforeSnapshotJson = beforeSnapshot?.let { journalJson.encodeToString(it) },
            afterSnapshotJson = afterSnapshot?.let { journalJson.encodeToString(it) },
            expectedUpdatedAt = patch.expectedUpdatedAt ?: 0L,
            revision = result.revision,
            serverUpdatedAt = result.serverUpdatedAt,
            serverPayloadJson = result.serverPayloadJson,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
        )
    }

    private suspend fun applyPatch(identity: AiMutationIdentity, patch: RecordPatch): PatchOutcome {
        val kind = SyncEntityKind.fromValue(patch.entityKind)
            ?: return invalidOutcome(patch, "记录类型无效")
        if (kind != SyncEntityKind.CASH_FLOW && kind != SyncEntityKind.TRANSFER) {
            return invalidOutcome(patch, "补丁只支持收支和转账记录")
        }
        return when (patch.op?.lowercase()) {
            null -> invalidOutcome(patch, "缺少 op 字段")
            OP_CREATE -> applyCreate(identity, patch, kind)
            OP_UPDATE -> applyUpdate(patch, kind)
            OP_DELETE -> applyDelete(patch, kind)
            else -> invalidOutcome(patch, "op 必须是 create、update 或 delete")
        }
    }

    private suspend fun applyCreate(
        identity: AiMutationIdentity,
        patch: RecordPatch,
        kind: SyncEntityKind,
    ): PatchOutcome {
        if (patch.recordId != null || patch.expectedUpdatedAt != null) {
            return invalidOutcome(patch, "create 补丁不应携带 recordId 或 expectedUpdatedAt")
        }
        val occurredAt = if (FIELD_OCCURRED_AT in patch.changes) {
            patch.changes.long(FIELD_OCCURRED_AT) ?: return invalidOutcome(patch, "occurredAt 无效")
        } else {
            clockProvider.nowMillis()
        }
        val note = patch.changes.string(FIELD_NOTE)?.trim() ?: ""
        if (note.length > MAX_SYNC_NOTE_LENGTH) {
            return invalidOutcome(patch, "备注长度不能超过 $MAX_SYNC_NOTE_LENGTH 字")
        }
        val amount = patch.changes.string(FIELD_AMOUNT)?.toLongOrNull()?.takeIf { it > 0L }
            ?: return invalidOutcome(patch, "amount 必须是正的最小货币单位整数字符串")
        val operationId = "ai:${identity.requestId}:${patch.patchId}"
        return try {
            val result = when (kind) {
                SyncEntityKind.CASH_FLOW -> {
                    rejectUnknownFields(patch, CASH_FLOW_WRITE_FIELDS)
                    val accountId = patch.changes.long(FIELD_ACCOUNT_ID)
                        ?: return invalidOutcome(patch, "changes.accountId 缺失或无效")
                    val direction = patch.changes.string(FIELD_DIRECTION)
                        ?.let(::parseDirection)
                        ?: return invalidOutcome(patch, "direction 必须是 inflow 或 outflow")
                    createCashFlowRecordUseCase(accountId, direction, amount, note, occurredAt, operationId)
                }
                SyncEntityKind.TRANSFER -> {
                    rejectUnknownFields(patch, TRANSFER_WRITE_FIELDS)
                    val fromAccountId = patch.changes.long(FIELD_FROM_ACCOUNT_ID)
                        ?: return invalidOutcome(patch, "changes.fromAccountId 缺失或无效")
                    val toAccountId = patch.changes.long(FIELD_TO_ACCOUNT_ID)
                        ?: return invalidOutcome(patch, "changes.toAccountId 缺失或无效")
                    createTransferRecordUseCase(fromAccountId, toAccountId, amount, note, occurredAt, operationId)
                }
                else -> error("unreachable")
            }
            if (!result.inserted) return invalidOutcome(patch, "操作标识冲突，请更换 patchId")
            val after = requireNotNull(queryStoredSnapshot(kind, result.recordId))
            PatchOutcome(
                result = PatchResult(
                    patchId = patch.patchId,
                    status = SyncPatchStatus.APPLIED,
                    recordId = result.recordId,
                    revision = syncRepository.queryLatestRevisionFor(kind, result.recordId),
                ),
                beforeSnapshot = null,
                afterSnapshot = after,
            )
        } catch (error: IllegalArgumentException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        } catch (error: IllegalStateException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        }
    }

    private suspend fun applyUpdate(patch: RecordPatch, kind: SyncEntityKind): PatchOutcome {
        val recordId = patch.recordId
        val expectedUpdatedAt = patch.expectedUpdatedAt
        if (recordId == null || expectedUpdatedAt == null) {
            return invalidOutcome(patch, "update 补丁必须携带 recordId 和 expectedUpdatedAt")
        }
        if (patch.changes.isEmpty()) return invalidOutcome(patch, "update 补丁的 changes 不能为空")
        val current = queryStoredSnapshot(kind, recordId)
            ?: return invalidOutcome(patch, "记录不存在或已删除")
        if (current.deletedAt != null || current.updatedAt != expectedUpdatedAt) {
            return conflictOutcome(patch, current)
        }
        return try {
            when (kind) {
                SyncEntityKind.CASH_FLOW -> {
                    rejectUnknownFields(patch, CASH_FLOW_WRITE_FIELDS)
                    updateCashFlowRecordUseCase(
                        recordId = recordId,
                        accountId = patch.changes.long(FIELD_ACCOUNT_ID) ?: requireNotNull(current.accountId),
                        direction = patch.changes.string(FIELD_DIRECTION)?.let {
                            parseDirection(it)
                                ?: throw IllegalArgumentException("direction 必须是 inflow 或 outflow")
                        } ?: CashFlowDirection.fromValue(requireNotNull(current.direction)),
                        amount = patch.changes.amountOrNull() ?: current.amount,
                        note = patch.changes.noteOrNull() ?: (current.note ?: ""),
                        occurredAt = patch.changes.optionalLong(FIELD_OCCURRED_AT) ?: current.occurredAt,
                        expectedUpdatedAt = current.updatedAt,
                    )
                }
                SyncEntityKind.TRANSFER -> {
                    rejectUnknownFields(patch, TRANSFER_WRITE_FIELDS)
                    updateTransferRecordUseCase(
                        recordId = recordId,
                        fromAccountId = patch.changes.long(FIELD_FROM_ACCOUNT_ID)
                            ?: requireNotNull(current.fromAccountId),
                        toAccountId = patch.changes.long(FIELD_TO_ACCOUNT_ID)
                            ?: requireNotNull(current.toAccountId),
                        amount = patch.changes.amountOrNull() ?: current.amount,
                        note = patch.changes.noteOrNull() ?: (current.note ?: ""),
                        occurredAt = patch.changes.optionalLong(FIELD_OCCURRED_AT) ?: current.occurredAt,
                        expectedUpdatedAt = current.updatedAt,
                    )
                }
                else -> error("unreachable")
            }
            PatchOutcome(
                result = PatchResult(
                    patchId = patch.patchId,
                    status = SyncPatchStatus.APPLIED,
                    recordId = recordId,
                    revision = syncRepository.queryLatestRevisionFor(kind, recordId),
                ),
                beforeSnapshot = current,
                afterSnapshot = requireNotNull(queryStoredSnapshot(kind, recordId)),
            )
        } catch (error: LedgerRecordChangedException) {
            conflictOutcome(patch, queryStoredSnapshot(kind, recordId))
        } catch (error: IllegalArgumentException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        } catch (error: IllegalStateException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        }
    }

    private suspend fun applyDelete(patch: RecordPatch, kind: SyncEntityKind): PatchOutcome {
        val recordId = patch.recordId
        val expectedUpdatedAt = patch.expectedUpdatedAt
        if (recordId == null || expectedUpdatedAt == null) {
            return invalidOutcome(patch, "delete 补丁必须携带 recordId 和 expectedUpdatedAt")
        }
        if (!patch.changes.isEmpty()) return invalidOutcome(patch, "delete 补丁不应携带 changes")
        val current = queryStoredSnapshot(kind, recordId)
            ?: return invalidOutcome(patch, "记录不存在或已删除")
        if (current.deletedAt != null || current.updatedAt != expectedUpdatedAt) {
            return conflictOutcome(patch, current)
        }
        return try {
            when (kind) {
                SyncEntityKind.CASH_FLOW -> deleteCashFlowRecordUseCase(recordId, current.updatedAt)
                SyncEntityKind.TRANSFER -> deleteTransferRecordUseCase(recordId, current.updatedAt)
                else -> error("unreachable")
            } ?: return invalidOutcome(patch, "记录不存在或已删除")
            PatchOutcome(
                result = PatchResult(
                    patchId = patch.patchId,
                    status = SyncPatchStatus.APPLIED,
                    recordId = recordId,
                    revision = syncRepository.queryLatestRevisionFor(kind, recordId),
                ),
                beforeSnapshot = current,
                afterSnapshot = requireNotNull(queryStoredSnapshot(kind, recordId)),
            )
        } catch (error: LedgerRecordChangedException) {
            conflictOutcome(patch, queryStoredSnapshot(kind, recordId))
        } catch (error: IllegalArgumentException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        } catch (error: IllegalStateException) {
            invalidOutcome(patch, error.message ?: "补丁无法应用")
        }
    }

    private fun rejectUnknownFields(patch: RecordPatch, allowed: Set<String>) {
        val unknown = patch.changes.keys - allowed
        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException("changes 包含不支持的字段：${unknown.sorted().joinToString(", ")}")
        }
    }

    /** Trimmed note within the sync limit, or null when the field is absent. Throws on overflow. */
    private fun JsonObject.noteOrNull(): String? {
        val raw = string(FIELD_NOTE) ?: return null
        val note = raw.trim()
        require(note.length <= MAX_SYNC_NOTE_LENGTH) { "备注长度不能超过 $MAX_SYNC_NOTE_LENGTH 字" }
        return note
    }

    /** Positive minor-unit amount parsed from the wire string, or null when absent. Throws on bad input. */
    private fun JsonObject.amountOrNull(): Long? {
        val raw = string(FIELD_AMOUNT) ?: return null
        val amount = raw.toLongOrNull()
        require(amount != null && amount > 0L) { "amount 必须是正的最小货币单位整数字符串" }
        return amount
    }

    private fun JsonObject.string(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * Strict wire parsing: unlike [CashFlowDirection.fromValue] (which falls back to INFLOW for
     * stored-data robustness), an unknown wire value must reject the patch, never silently book
     * an inflow.
     */
    private fun parseDirection(raw: String): CashFlowDirection? = when (raw) {
        CashFlowDirection.INFLOW.value -> CashFlowDirection.INFLOW
        CashFlowDirection.OUTFLOW.value -> CashFlowDirection.OUTFLOW
        else -> null
    }

    private fun JsonObject.long(field: String): Long? =
        (this[field] as? JsonPrimitive)?.longOrNull

    /** Like [long] but rejects a present-but-unparseable value instead of treating it as absent. */
    private fun JsonObject.optionalLong(field: String): Long? {
        if (field !in this) return null
        return long(field) ?: throw IllegalArgumentException("$field 无效")
    }

    private suspend fun conflictOutcome(
        patch: RecordPatch,
        current: AiLedgerRecordSnapshot?,
    ): PatchOutcome = PatchOutcome(
        result = PatchResult(
            patchId = patch.patchId,
            status = SyncPatchStatus.CONFLICT,
            recordId = patch.recordId,
            serverUpdatedAt = current?.updatedAt,
            serverPayloadJson = current?.let {
                mirrorPayloadJson(requireNotNull(SyncEntityKind.fromValue(patch.entityKind)), it.recordId)
            },
        ),
        beforeSnapshot = null,
        afterSnapshot = null,
    )

    private fun invalidOutcome(patch: RecordPatch, message: String): PatchOutcome = PatchOutcome(
        result = PatchResult(
            patchId = patch.patchId,
            status = SyncPatchStatus.INVALID,
            recordId = patch.recordId,
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

    companion object {
        const val MAX_PATCHES_PER_BATCH = 50
        const val OP_CREATE = "create"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "delete"
        const val FIELD_ACCOUNT_ID = "accountId"
        const val FIELD_FROM_ACCOUNT_ID = "fromAccountId"
        const val FIELD_TO_ACCOUNT_ID = "toAccountId"
        const val FIELD_DIRECTION = "direction"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_NOTE = "note"
        const val FIELD_OCCURRED_AT = "occurredAt"
        /** Same trim + length policy as the note pipeline and the UI's normalizeLedgerNote. */
        const val MAX_SYNC_NOTE_LENGTH = 200

        private val CASH_FLOW_WRITE_FIELDS = setOf(
            FIELD_ACCOUNT_ID,
            FIELD_DIRECTION,
            FIELD_AMOUNT,
            FIELD_NOTE,
            FIELD_OCCURRED_AT,
        )
        private val TRANSFER_WRITE_FIELDS = setOf(
            FIELD_FROM_ACCOUNT_ID,
            FIELD_TO_ACCOUNT_ID,
            FIELD_AMOUNT,
            FIELD_NOTE,
            FIELD_OCCURRED_AT,
        )

        private val journalJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
