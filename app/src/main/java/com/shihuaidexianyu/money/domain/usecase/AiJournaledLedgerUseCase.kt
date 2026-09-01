package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationAction
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.domain.model.AiMutationReceipt
import com.shihuaidexianyu.money.domain.model.AiUndoBatchItem
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.model.semanticallyEquals
import com.shihuaidexianyu.money.domain.model.sync.SyncPatchStatus
import com.shihuaidexianyu.money.domain.model.toAiLedgerSnapshot
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AiMutationIdentity(
    val requestId: String,
    val sessionId: String,
    val clientName: String,
)

data class AiCreateCashFlowCommand(
    val identity: AiMutationIdentity,
    val accountId: Long,
    val direction: CashFlowDirection,
    val amount: Long,
    val note: String,
    /** Null means the phone clock is authoritative for this newly created record. */
    val occurredAt: Long? = null,
)

data class AiUpdateCashFlowCommand(
    val identity: AiMutationIdentity,
    val recordId: Long,
    val accountId: Long,
    val direction: CashFlowDirection,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val expectedUpdatedAt: Long? = null,
)

data class AiCreateTransferCommand(
    val identity: AiMutationIdentity,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
    /** Null means the phone clock is authoritative for this newly created record. */
    val occurredAt: Long? = null,
)

data class AiUpdateTransferCommand(
    val identity: AiMutationIdentity,
    val recordId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val expectedUpdatedAt: Long? = null,
)

class AiJournaledLedgerUseCase(
    private val journalRepository: AiMutationJournalRepository,
    private val transactionRepository: TransactionRepository,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val updateCashFlowRecordUseCase: UpdateCashFlowRecordUseCase,
    private val deleteCashFlowRecordUseCase: DeleteCashFlowRecordUseCase,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
    private val restoreLedgerRecordUseCase: RestoreLedgerRecordUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend fun createCashFlow(command: AiCreateCashFlowCommand): AiMutationReceipt =
        transactionRepository.runInTransaction {
            replay(command.identity)?.let { return@runInTransaction it }
            validateIdentity(command.identity)
            val result = createCashFlowRecordUseCase(
                accountId = command.accountId,
                direction = command.direction,
                amount = command.amount,
                note = command.note,
                occurredAt = command.occurredAt ?: clockProvider.nowMillis(),
                operationId = operationId(command.identity.requestId),
            )
            check(result.inserted) { "操作标识已存在，但对应的 AI Journal 不存在" }
            val after = requireNotNull(transactionRepository.queryStoredCashFlowRecordById(result.recordId))
                .toAiLedgerSnapshot()
            insertEntry(
                identity = command.identity,
                action = AiMutationAction.CREATE_CASH_FLOW,
                after = after,
                summary = "AI 新增收支记录 #${result.recordId}",
            )
        }

    suspend fun updateCashFlow(command: AiUpdateCashFlowCommand): AiMutationReceipt =
        transactionRepository.runInTransaction {
            replay(command.identity)?.let { return@runInTransaction it }
            validateIdentity(command.identity)
            val beforeRecord = requireNotNull(transactionRepository.queryCashFlowRecordById(command.recordId)) {
                "记录不存在或已删除"
            }
            requireExpectedRevision(
                expectedUpdatedAt = command.expectedUpdatedAt,
                actualUpdatedAt = beforeRecord.updatedAt,
                kind = LedgerRecordKind.CASH_FLOW,
                recordId = command.recordId,
            )
            updateCashFlowRecordUseCase(
                recordId = command.recordId,
                accountId = command.accountId,
                direction = command.direction,
                amount = command.amount,
                note = command.note,
                occurredAt = command.occurredAt,
                expectedUpdatedAt = beforeRecord.updatedAt,
            )
            val after = requireNotNull(transactionRepository.queryStoredCashFlowRecordById(command.recordId))
                .toAiLedgerSnapshot()
            insertEntry(
                identity = command.identity,
                action = AiMutationAction.UPDATE_CASH_FLOW,
                before = beforeRecord.toAiLedgerSnapshot(),
                after = after,
                summary = "AI 修改收支记录 #${command.recordId}",
            )
        }

    suspend fun deleteCashFlow(
        identity: AiMutationIdentity,
        recordId: Long,
        expectedUpdatedAt: Long? = null,
    ): AiMutationReceipt = transactionRepository.runInTransaction {
        replay(identity)?.let { return@runInTransaction it }
        validateIdentity(identity)
        val beforeRecord = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId)) {
            "记录不存在或已删除"
        }
        requireExpectedRevision(
            expectedUpdatedAt = expectedUpdatedAt,
            actualUpdatedAt = beforeRecord.updatedAt,
            kind = LedgerRecordKind.CASH_FLOW,
            recordId = recordId,
        )
        val token = requireNotNull(deleteCashFlowRecordUseCase(recordId, beforeRecord.updatedAt)) {
            "记录不存在或已删除"
        }
        val after = requireNotNull(transactionRepository.queryStoredCashFlowRecordById(recordId)).toAiLedgerSnapshot()
        insertEntry(
            identity = identity,
            action = AiMutationAction.DELETE_CASH_FLOW,
            before = beforeRecord.toAiLedgerSnapshot(),
            after = after,
            undoToken = token,
            summary = "AI 删除收支记录 #$recordId",
        )
    }

    suspend fun createTransfer(command: AiCreateTransferCommand): AiMutationReceipt =
        transactionRepository.runInTransaction {
            replay(command.identity)?.let { return@runInTransaction it }
            validateIdentity(command.identity)
            val result = createTransferRecordUseCase(
                fromAccountId = command.fromAccountId,
                toAccountId = command.toAccountId,
                amount = command.amount,
                note = command.note,
                occurredAt = command.occurredAt ?: clockProvider.nowMillis(),
                operationId = operationId(command.identity.requestId),
            )
            check(result.inserted) { "操作标识已存在，但对应的 AI Journal 不存在" }
            val after = requireNotNull(transactionRepository.queryStoredTransferRecordById(result.recordId))
                .toAiLedgerSnapshot()
            insertEntry(
                identity = command.identity,
                action = AiMutationAction.CREATE_TRANSFER,
                after = after,
                summary = "AI 新增转账记录 #${result.recordId}",
            )
        }

    suspend fun updateTransfer(command: AiUpdateTransferCommand): AiMutationReceipt =
        transactionRepository.runInTransaction {
            replay(command.identity)?.let { return@runInTransaction it }
            validateIdentity(command.identity)
            val beforeRecord = requireNotNull(transactionRepository.queryTransferRecordById(command.recordId)) {
                "记录不存在或已删除"
            }
            requireExpectedRevision(
                expectedUpdatedAt = command.expectedUpdatedAt,
                actualUpdatedAt = beforeRecord.updatedAt,
                kind = LedgerRecordKind.TRANSFER,
                recordId = command.recordId,
            )
            updateTransferRecordUseCase(
                recordId = command.recordId,
                fromAccountId = command.fromAccountId,
                toAccountId = command.toAccountId,
                amount = command.amount,
                note = command.note,
                occurredAt = command.occurredAt,
                expectedUpdatedAt = beforeRecord.updatedAt,
            )
            val after = requireNotNull(transactionRepository.queryStoredTransferRecordById(command.recordId))
                .toAiLedgerSnapshot()
            insertEntry(
                identity = command.identity,
                action = AiMutationAction.UPDATE_TRANSFER,
                before = beforeRecord.toAiLedgerSnapshot(),
                after = after,
                summary = "AI 修改转账记录 #${command.recordId}",
            )
        }

    suspend fun deleteTransfer(
        identity: AiMutationIdentity,
        recordId: Long,
        expectedUpdatedAt: Long? = null,
    ): AiMutationReceipt = transactionRepository.runInTransaction {
        replay(identity)?.let { return@runInTransaction it }
        validateIdentity(identity)
        val beforeRecord = requireNotNull(transactionRepository.queryTransferRecordById(recordId)) {
            "记录不存在或已删除"
        }
        requireExpectedRevision(
            expectedUpdatedAt = expectedUpdatedAt,
            actualUpdatedAt = beforeRecord.updatedAt,
            kind = LedgerRecordKind.TRANSFER,
            recordId = recordId,
        )
        val token = requireNotNull(deleteTransferRecordUseCase(recordId, beforeRecord.updatedAt)) {
            "记录不存在或已删除"
        }
        val after = requireNotNull(transactionRepository.queryStoredTransferRecordById(recordId)).toAiLedgerSnapshot()
        insertEntry(
            identity = identity,
            action = AiMutationAction.DELETE_TRANSFER,
            before = beforeRecord.toAiLedgerSnapshot(),
            after = after,
            undoToken = token,
            summary = "AI 删除转账记录 #$recordId",
        )
    }

    suspend fun undoLatest(undoRequestId: String): UndoLatestAiMutationResult =
        transactionRepository.runInTransaction {
            requireValidRequestId(undoRequestId)
            journalRepository.queryByUndoRequestId(undoRequestId)?.let { replayed ->
                return@runInTransaction replayedUndo(replayed)
            }
            val entry = journalRepository.queryLatestApplied()
                ?: return@runInTransaction UndoLatestAiMutationResult.Empty
            if (entry.entryType == AiMutationEntryType.BATCH) {
                return@runInTransaction undoBatch(entry, undoRequestId)
            }
            val after = journalJson.decodeFromString<AiLedgerRecordSnapshot>(
                requireNotNull(entry.afterSnapshotJson) { "Journal 缺少操作后快照" },
            )
            val current = queryStoredSnapshot(after.kind, after.recordId)
            if (current == null || !after.semanticallyEquals(current)) {
                return@runInTransaction UndoLatestAiMutationResult.Conflict(
                    entry = entry,
                    message = "当前记录与 Journal 中的操作后快照不一致，已停止撤销以免覆盖新数据",
                )
            }

            when (entry.action) {
                AiMutationAction.CREATE_CASH_FLOW -> {
                    requireNotNull(deleteCashFlowRecordUseCase(requireNotNull(entry.recordId), current.updatedAt))
                }
                AiMutationAction.CREATE_TRANSFER -> {
                    requireNotNull(deleteTransferRecordUseCase(requireNotNull(entry.recordId), current.updatedAt))
                }
                AiMutationAction.UPDATE_CASH_FLOW,
                AiMutationAction.UPDATE_TRANSFER,
                -> restoreSnapshot(decodeBefore(entry), current)
                AiMutationAction.DELETE_CASH_FLOW,
                AiMutationAction.DELETE_TRANSFER,
                -> restoreDeletedRecord(entry)
                AiMutationAction.BATCH_NOTE_UPDATE ->
                    error("批量条目必须通过批量撤销路径处理")
            }

            val undoneAt = clockProvider.nowMillis()
            check(journalRepository.markUndone(entry.id, undoneAt, undoRequestId)) {
                "Journal 栈顶已发生变化，请重试"
            }
            UndoLatestAiMutationResult.Undone(
                entry = entry.copy(
                    status = AiMutationJournalStatus.UNDONE,
                    resolvedAt = undoneAt,
                    undoRequestId = undoRequestId,
                ),
                replayed = false,
            )
        }

    suspend fun discardLatest(entryId: Long): Boolean = transactionRepository.runInTransaction {
        journalRepository.discardLatest(entryId, clockProvider.nowMillis())
    }

    private suspend fun replayedUndo(entry: AiMutationJournalEntry): UndoLatestAiMutationResult =
        if (entry.entryType == AiMutationEntryType.BATCH) {
            UndoLatestAiMutationResult.BatchUndone(
                entry = entry,
                items = restoredBatchItems(entry),
                replayed = true,
            )
        } else {
            UndoLatestAiMutationResult.Undone(entry, replayed = true)
        }

    /**
     * Batch undo is all-or-nothing: every applied item's current state must still semantically
     * equal its after-snapshot (updatedAt ignored); any drift rejects the whole batch before a
     * single record is touched. Only then are all before-snapshots restored via the leaf use
     * cases, so the sync change-log sees one upsert per restored record.
     */
    private suspend fun undoBatch(
        entry: AiMutationJournalEntry,
        undoRequestId: String,
    ): UndoLatestAiMutationResult {
        val appliedItems = journalRepository.queryItems(entry.id)
            .filter { it.status == SyncPatchStatus.APPLIED.value }
        val prechecked = appliedItems.map { item ->
            val after = journalJson.decodeFromString<AiLedgerRecordSnapshot>(
                requireNotNull(item.afterSnapshotJson) { "批量 Journal 缺少操作后快照" },
            )
            val current = queryStoredSnapshot(after.kind, after.recordId)
            BatchItemPrecheck(
                item = item,
                after = after,
                current = current,
                drifted = current == null || !after.semanticallyEquals(current),
            )
        }
        if (prechecked.any { it.drifted }) {
            return UndoLatestAiMutationResult.BatchConflict(
                entry = entry,
                message = "批量操作中的记录已被修改，已整体停止撤销以免覆盖新数据",
                items = prechecked.map { precheck ->
                    AiUndoBatchItem(
                        recordKind = precheck.after.kind,
                        recordId = precheck.after.recordId,
                        restored = false,
                        message = if (precheck.drifted) "记录当前状态与批量修改后的快照不一致" else null,
                    )
                },
            )
        }

        prechecked.forEach { precheck ->
            val before = journalJson.decodeFromString<AiLedgerRecordSnapshot>(
                requireNotNull(precheck.item.beforeSnapshotJson) { "批量 Journal 缺少操作前快照" },
            )
            restoreSnapshot(before, requireNotNull(precheck.current))
        }
        val undoneAt = clockProvider.nowMillis()
        journalRepository.markItemsUndone(entry.id)
        check(journalRepository.markUndone(entry.id, undoneAt, undoRequestId)) {
            "Journal 栈顶已发生变化，请重试"
        }
        return UndoLatestAiMutationResult.BatchUndone(
            entry = entry.copy(
                status = AiMutationJournalStatus.UNDONE,
                resolvedAt = undoneAt,
                undoRequestId = undoRequestId,
            ),
            items = prechecked.map { precheck ->
                AiUndoBatchItem(
                    recordKind = precheck.after.kind,
                    recordId = precheck.after.recordId,
                    restored = true,
                )
            },
            replayed = false,
        )
    }

    private suspend fun restoredBatchItems(entry: AiMutationJournalEntry): List<AiUndoBatchItem> =
        journalRepository.queryItems(entry.id)
            .filter { it.status == SyncPatchStatus.APPLIED.value }
            .map { item ->
                val after = journalJson.decodeFromString<AiLedgerRecordSnapshot>(
                    requireNotNull(item.afterSnapshotJson) { "批量 Journal 缺少操作后快照" },
                )
                AiUndoBatchItem(recordKind = after.kind, recordId = after.recordId, restored = true)
            }

    private class BatchItemPrecheck(
        val item: AiMutationJournalItem,
        val after: AiLedgerRecordSnapshot,
        val current: AiLedgerRecordSnapshot?,
        val drifted: Boolean,
    )

    private suspend fun restoreSnapshot(
        before: AiLedgerRecordSnapshot,
        current: AiLedgerRecordSnapshot,
    ) {
        when (before.kind) {
            LedgerRecordKind.CASH_FLOW -> updateCashFlowRecordUseCase(
                recordId = before.recordId,
                accountId = requireNotNull(before.accountId),
                direction = CashFlowDirection.fromValue(requireNotNull(before.direction)),
                amount = before.amount,
                note = before.note.orEmpty(),
                occurredAt = before.occurredAt,
                preserveNoteVerbatim = true,
                expectedUpdatedAt = current.updatedAt,
            )
            LedgerRecordKind.TRANSFER -> updateTransferRecordUseCase(
                recordId = before.recordId,
                fromAccountId = requireNotNull(before.fromAccountId),
                toAccountId = requireNotNull(before.toAccountId),
                amount = before.amount,
                note = before.note.orEmpty(),
                occurredAt = before.occurredAt,
                preserveNoteVerbatim = true,
                expectedUpdatedAt = current.updatedAt,
            )
            LedgerRecordKind.BALANCE_UPDATE,
            LedgerRecordKind.BALANCE_ADJUSTMENT,
            -> error("Journal 快照类型不受支持")
        }
    }

    private suspend fun restoreDeletedRecord(entry: AiMutationJournalEntry) {
        val tokenJson = requireNotNull(entry.undoTokenJson) { "Journal 缺少恢复令牌" }
        val token = journalJson.decodeFromString<LedgerUndoToken>(tokenJson)
        check(restoreLedgerRecordUseCase(token) == RestoreLedgerResult.RESTORED) {
            "账本记录已变化，无法安全恢复"
        }
    }

    private fun decodeBefore(entry: AiMutationJournalEntry): AiLedgerRecordSnapshot =
        journalJson.decodeFromString(requireNotNull(entry.beforeSnapshotJson) { "Journal 缺少操作前快照" })

    private suspend fun queryStoredSnapshot(kind: LedgerRecordKind, recordId: Long): AiLedgerRecordSnapshot? =
        when (kind) {
            LedgerRecordKind.CASH_FLOW -> transactionRepository.queryStoredCashFlowRecordById(recordId)
                ?.toAiLedgerSnapshot()
            LedgerRecordKind.TRANSFER -> transactionRepository.queryStoredTransferRecordById(recordId)
                ?.toAiLedgerSnapshot()
            LedgerRecordKind.BALANCE_UPDATE,
            LedgerRecordKind.BALANCE_ADJUSTMENT,
            -> null
        }

    private suspend fun replay(identity: AiMutationIdentity): AiMutationReceipt? =
        journalRepository.queryByRequestId(identity.requestId)?.let { AiMutationReceipt(it, replayed = true) }

    private suspend fun insertEntry(
        identity: AiMutationIdentity,
        action: AiMutationAction,
        after: AiLedgerRecordSnapshot,
        summary: String,
        before: AiLedgerRecordSnapshot? = null,
        undoToken: LedgerUndoToken? = null,
    ): AiMutationReceipt {
        val entry = AiMutationJournalEntry(
            requestId = identity.requestId,
            sessionId = identity.sessionId,
            clientName = identity.clientName,
            action = action,
            recordKind = after.kind,
            recordId = after.recordId,
            summary = summary,
            beforeSnapshotJson = before?.let(journalJson::encodeToString),
            afterSnapshotJson = journalJson.encodeToString(after),
            undoTokenJson = undoToken?.let(journalJson::encodeToString),
            createdAt = clockProvider.nowMillis(),
        )
        val id = journalRepository.insert(entry)
        return AiMutationReceipt(entry.copy(id = id), replayed = false)
    }

    private fun validateIdentity(identity: AiMutationIdentity) {
        requireValidRequestId(identity.requestId)
        require(identity.sessionId.isNotBlank() && identity.sessionId.length <= 128) { "会话标识无效" }
        require(identity.clientName.isNotBlank() && identity.clientName.length <= 80) { "客户端名称无效" }
    }

    private fun requireValidRequestId(requestId: String) {
        require(requestId.isNotBlank() && requestId.length <= 128) { "请求标识无效" }
    }

    private fun requireExpectedRevision(
        expectedUpdatedAt: Long?,
        actualUpdatedAt: Long,
        kind: LedgerRecordKind,
        recordId: Long,
    ) {
        if (expectedUpdatedAt != null && expectedUpdatedAt != actualUpdatedAt) {
            throw LedgerRecordChangedException(kind, recordId)
        }
    }

    private fun operationId(requestId: String): String = "ai:$requestId"

    private companion object {
        val journalJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
