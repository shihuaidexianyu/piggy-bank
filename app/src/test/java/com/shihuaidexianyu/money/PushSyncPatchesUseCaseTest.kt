package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemorySyncRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.model.sync.NotePatch
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncPatchStatus
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.AiCreateCashFlowCommand
import com.shihuaidexianyu.money.domain.usecase.AiJournaledLedgerUseCase
import com.shihuaidexianyu.money.domain.usecase.AiMutationIdentity
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.PushSyncPatchesUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Shared fixture for sync.push and batch-undo tests: real leaf use cases over in-memory repos. */
internal class SyncPushFixture {
    val accounts = InMemoryAccountRepository()
    val transactions = InMemoryTransactionRepository()
    val journal = FakeBatchJournalRepository()
    val clock = MutableClock(1_000L)
    val syncRepository = InMemorySyncRepository(
        datasetIdGenerator = { "ds-push" },
        createdAtProvider = { 0L },
    )

    private val transactionPort = object : TransactionRepository by transactions {
        // The production Room runner is re-entrant; the in-memory one is not, so flatten.
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    // The helper casts to LedgerAggregateRepository, which only the concrete in-memory repo is.
    private val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
    private val append = AppendSyncChangesUseCase(syncRepository, clock)

    private val createCash = CreateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val updateCash = UpdateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val deleteCash = DeleteCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val createTransfer = CreateTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val updateTransfer = UpdateTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val deleteTransfer = DeleteTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
    private val restore = RestoreLedgerRecordUseCase(accounts, transactionPort, refresh, clock, appendSyncChangesUseCase = append)

    val push = PushSyncPatchesUseCase(
        journalRepository = journal,
        transactionRepository = transactionPort,
        syncRepository = syncRepository,
        updateCashFlowRecordUseCase = updateCash,
        updateTransferRecordUseCase = updateTransfer,
        clockProvider = clock,
    )

    val journaled = AiJournaledLedgerUseCase(
        journalRepository = journal,
        transactionRepository = transactionPort,
        createCashFlowRecordUseCase = createCash,
        updateCashFlowRecordUseCase = updateCash,
        deleteCashFlowRecordUseCase = deleteCash,
        createTransferRecordUseCase = createTransfer,
        updateTransferRecordUseCase = updateTransfer,
        deleteTransferRecordUseCase = deleteTransfer,
        restoreLedgerRecordUseCase = restore,
        clockProvider = clock,
    )

    suspend fun newAccount(name: String): Long =
        accounts.createAccount(Account(name = name, initialBalance = 0, createdAt = clock.now))

    suspend fun newCashFlow(accountId: Long, note: String = "原备注"): Long =
        createCash(accountId, CashFlowDirection.OUTFLOW, 100, note, 10, "op-${note.hashCode()}").recordId

    suspend fun newTransfer(from: Long, to: Long, note: String = "转账备注"): Long =
        createTransfer(from, to, 50, note, 10, "op-tf-${note.hashCode()}").recordId

    fun identity(requestId: String) = AiMutationIdentity(requestId, "session", "test-client")

    fun notePatch(
        patchId: String,
        kind: String,
        recordId: Long,
        expectedUpdatedAt: Long,
        note: String,
    ) = NotePatch(
        patchId = patchId,
        entityKind = kind,
        recordId = recordId,
        expectedUpdatedAt = expectedUpdatedAt,
        changes = mapOf("note" to note),
    )
}

internal class MutableClock(var now: Long) : ClockProvider {
    override fun nowMillis(): Long = now
}

internal class FakeBatchJournalRepository : AiMutationJournalRepository {
    private val entries = MutableStateFlow<List<AiMutationJournalEntry>>(emptyList())
    private val batchItems = mutableMapOf<Long, List<AiMutationJournalItem>>()
    private var nextId = 1L

    override fun observeRecent(limit: Int): Flow<List<AiMutationJournalEntry>> =
        entries.map { it.sortedByDescending(AiMutationJournalEntry::id).take(limit) }

    override fun observeAppliedCount(): Flow<Int> =
        entries.map { rows -> rows.count { it.status == AiMutationJournalStatus.APPLIED } }

    override fun observeLatestApplied(): Flow<AiMutationJournalEntry?> =
        entries.map { rows -> rows.filter { it.status == AiMutationJournalStatus.APPLIED }.maxByOrNull { it.id } }

    override suspend fun queryRecent(limit: Int): List<AiMutationJournalEntry> =
        entries.value.sortedByDescending(AiMutationJournalEntry::id).take(limit)

    override suspend fun queryByRequestId(requestId: String): AiMutationJournalEntry? =
        entries.value.firstOrNull { it.requestId == requestId }

    override suspend fun queryByUndoRequestId(requestId: String): AiMutationJournalEntry? =
        entries.value.firstOrNull { it.undoRequestId == requestId }

    override suspend fun queryLatestApplied(): AiMutationJournalEntry? =
        entries.value.filter { it.status == AiMutationJournalStatus.APPLIED }.maxByOrNull { it.id }

    override suspend fun insert(entry: AiMutationJournalEntry): Long {
        check(entries.value.none { it.requestId == entry.requestId })
        val id = nextId++
        entries.value = entries.value + entry.copy(id = id)
        return id
    }

    override suspend fun insertBatch(entry: AiMutationJournalEntry, items: List<AiMutationJournalItem>): Long {
        val id = insert(entry)
        batchItems[id] = items.map { it.copy(journalId = id) }
        return id
    }

    override suspend fun queryItems(journalId: Long): List<AiMutationJournalItem> =
        batchItems[journalId].orEmpty().sortedBy { it.itemIndex }

    override suspend fun markItemsUndone(journalId: Long) {
        batchItems[journalId] = batchItems[journalId].orEmpty().map { it.copy(undoneAt = 1L) }
    }

    override suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Boolean {
        val existing = entries.value.firstOrNull { it.id == id } ?: return false
        if (existing.status != AiMutationJournalStatus.APPLIED) return false
        entries.value = entries.value.map {
            if (it.id == id) {
                it.copy(
                    status = AiMutationJournalStatus.UNDONE,
                    resolvedAt = undoneAt,
                    undoRequestId = undoRequestId,
                )
            } else {
                it
            }
        }
        return true
    }

    override suspend fun discardLatest(id: Long, discardedAt: Long): Boolean {
        if (queryLatestApplied()?.id != id) return false
        entries.value = entries.value.map {
            if (it.id == id) it.copy(status = AiMutationJournalStatus.DISCARDED, resolvedAt = discardedAt) else it
        }
        return true
    }

    override suspend fun deleteAll() {
        entries.value = emptyList()
        batchItems.clear()
    }
}

class PushSyncPatchesUseCaseTest {
    @Test
    fun appliedPatchUpdatesNoteAndJournalsBatchWithSnapshots() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId)
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))

        val result = fixture.push(
            fixture.identity("push-1"),
            expectedDatasetId = "ds-push",
            patches = listOf(fixture.notePatch("p-1", "cash_flow", recordId, stored.updatedAt, "  新备注  ")),
        )

        assertFalse(result.replayed)
        val patch = result.results.single()
        assertEquals(SyncPatchStatus.APPLIED, patch.status)
        // Revision 1 is the record creation; the push apply allocates revision 2.
        assertEquals(2L, patch.revision)

        val updated = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))
        assertEquals("新备注", updated.note)

        val entry = fixture.journal.queryRecent(10).single()
        assertEquals(AiMutationEntryType.BATCH, entry.entryType)
        assertEquals(1, entry.itemCount)
        assertEquals(1, entry.appliedCount)
        assertEquals(0, entry.conflictCount)
        assertNull(entry.recordId)
        assertEquals(AiMutationJournalStatus.APPLIED, entry.status)

        val item = fixture.journal.queryItems(entry.id).single()
        assertEquals("p-1", item.patchId)
        assertEquals("cash_flow", item.entityKind)
        assertEquals(stored.updatedAt, item.expectedUpdatedAt)
        assertEquals(2L, item.revision)
        val json = Json { ignoreUnknownKeys = true }
        val before = json.decodeFromString(AiLedgerRecordSnapshot.serializer(), assertNotNull(item.beforeSnapshotJson))
        val after = json.decodeFromString(AiLedgerRecordSnapshot.serializer(), assertNotNull(item.afterSnapshotJson))
        assertEquals("原备注", before.note)
        assertEquals("新备注", after.note)
    }

    @Test
    fun conflictCarriesServerStateAndStaleOrDeletedRecordsDoNotApply() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId, note = "服务器备注")
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))

        val result = fixture.push(
            fixture.identity("push-conflict"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                fixture.notePatch("p-stale", "cash_flow", recordId, stored.updatedAt + 999, "客户端备注"),
            ),
        )

        val patch = result.results.single()
        assertEquals(SyncPatchStatus.CONFLICT, patch.status)
        assertEquals(stored.updatedAt, patch.serverUpdatedAt)
        val payload = Json.parseToJsonElement(assertNotNull(patch.serverPayloadJson)).jsonObject
        assertEquals("服务器备注", payload.getValue("note").jsonPrimitive.content)
        // The record is untouched.
        assertEquals("服务器备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        // Zero applied patches: the batch never enters the undo stack.
        assertTrue(fixture.journal.queryRecent(10).isEmpty())

        // A retry with the same requestId is re-classified deterministically (conflict outcomes
        // are stable), still mutates nothing and still leaves no journal entry.
        val retry = fixture.push(
            fixture.identity("push-conflict"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                fixture.notePatch("p-stale", "cash_flow", recordId, stored.updatedAt + 999, "客户端备注"),
            ),
        )
        assertFalse(retry.replayed)
        assertEquals(result.results, retry.results)
        assertEquals("服务器备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        assertTrue(fixture.journal.queryRecent(10).isEmpty())
    }

    @Test
    fun invalidPatchesNeverFailTheBatch() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId)
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))
        val longNote = "x".repeat(PushSyncPatchesUseCase.MAX_SYNC_NOTE_LENGTH + 1)

        val result = fixture.push(
            fixture.identity("push-invalid"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                fixture.notePatch("p-kind", "balance_update", recordId, stored.updatedAt, "n"),
                NotePatch("p-field", "cash_flow", recordId, stored.updatedAt, mapOf("note" to "n", "amount" to "1")),
                fixture.notePatch("p-long", "cash_flow", recordId, stored.updatedAt, longNote),
                fixture.notePatch("p-missing", "cash_flow", 999_999, 1, "n"),
                fixture.notePatch("p-ok", "cash_flow", recordId, stored.updatedAt, "最终备注"),
            ),
        )

        assertEquals(
            listOf(
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.APPLIED,
            ),
            result.results.map { it.status },
        )
        assertTrue(result.results.take(4).all { it.errorCode == "INVALID_PATCH" })
        assertEquals("最终备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        val entry = fixture.journal.queryRecent(10).single()
        assertEquals(5, entry.itemCount)
        assertEquals(1, entry.appliedCount)
    }

    @Test
    fun datasetMismatchRejectsTheWholeBatch() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId)
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))

        assertFailsWith<SyncDatasetMismatchException> {
            fixture.push(
                fixture.identity("push-mismatch"),
                expectedDatasetId = "ds-other",
                patches = listOf(fixture.notePatch("p-1", "cash_flow", recordId, stored.updatedAt, "n")),
            )
        }
        assertEquals("原备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        assertTrue(fixture.journal.queryRecent(10).isEmpty())
    }

    @Test
    fun replayByRequestIdReturnsStoredResultsWithoutSideEffects() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId)
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))
        val patches = listOf(
            fixture.notePatch("p-1", "cash_flow", recordId, stored.updatedAt, "一次性备注"),
            // Far-future expectation can never match the CAS token after p-1 applies.
            fixture.notePatch("p-2", "cash_flow", recordId, stored.updatedAt + 999_999, "冲突备注"),
        )

        val first = fixture.push(fixture.identity("push-replay"), "ds-push", patches)
        val revisionAfterFirst = fixture.syncRepository.readDatasetState().currentRevision
        val replay = fixture.push(fixture.identity("push-replay"), "ds-push", patches)

        assertFalse(first.replayed)
        assertTrue(replay.replayed)
        assertEquals(first.results, replay.results)
        // No new journal entry, no new change-log revisions, no second mutation.
        assertEquals(1, fixture.journal.queryRecent(10).size)
        assertEquals(revisionAfterFirst, fixture.syncRepository.readDatasetState().currentRevision)
        assertEquals("一次性备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        // findStoredResults feeds the router's free-replay path.
        assertEquals(first.results, fixture.push.findStoredResults("push-replay"))
        assertNull(fixture.push.findStoredResults("push-unknown"))
    }
}

class BatchJournalUndoTest {
    @Test
    fun undoRestoresEveryAppliedPatchAndIsIdempotent() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val bankId = fixture.newAccount("招行")
        val cashId = fixture.newCashFlow(accountId, note = "饭前")
        val transferId = fixture.newTransfer(accountId, bankId, note = "转账前")
        fixture.clock.now = 2_000

        val cash = assertNotNull(fixture.transactions.queryCashFlowRecordById(cashId))
        val transfer = assertNotNull(fixture.transactions.queryTransferRecordById(transferId))
        fixture.push(
            fixture.identity("push-batch"),
            "ds-push",
            listOf(
                fixture.notePatch("p-1", "cash_flow", cashId, cash.updatedAt, "饭后"),
                fixture.notePatch("p-2", "transfer", transferId, transfer.updatedAt, "转账后"),
            ),
        )
        assertEquals("饭后", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertEquals("转账后", fixture.transactions.queryTransferRecordById(transferId)?.note)

        val undone = fixture.journaled.undoLatest("undo-batch")
        val batchUndone = assertIs<UndoLatestAiMutationResult.BatchUndone>(undone)
        assertFalse(batchUndone.replayed)
        assertEquals(2, batchUndone.items.size)
        assertTrue(batchUndone.items.all { it.restored })
        assertEquals("饭前", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertEquals("转账前", fixture.transactions.queryTransferRecordById(transferId)?.note)

        val entry = assertNotNull(fixture.journal.queryByRequestId("push-batch"))
        assertEquals(AiMutationJournalStatus.UNDONE, entry.status)
        assertTrue(fixture.journal.queryItems(entry.id).all { it.undoneAt != null })

        // Replayed undo request returns the same batch result without touching records again.
        val replayed = fixture.journaled.undoLatest("undo-batch")
        val replayedBatch = assertIs<UndoLatestAiMutationResult.BatchUndone>(replayed)
        assertTrue(replayedBatch.replayed)
        assertEquals(batchUndone.items, replayedBatch.items)
        assertEquals("饭前", fixture.transactions.queryCashFlowRecordById(cashId)?.note)

        // Undo went through the instrumented leaf use cases:
        // create + create + 2 push upserts + 2 restore upserts.
        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(6, changes.size)
        assertTrue(changes.all { it.operation == SyncChangeOperation.UPSERT })
    }

    @Test
    fun driftInAnyItemRejectsTheWholeBatchUndo() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val firstId = fixture.newCashFlow(accountId, note = "一前")
        val secondId = fixture.newCashFlow(accountId, note = "二前")

        val first = assertNotNull(fixture.transactions.queryCashFlowRecordById(firstId))
        val second = assertNotNull(fixture.transactions.queryCashFlowRecordById(secondId))
        fixture.push(
            fixture.identity("push-batch"),
            "ds-push",
            listOf(
                fixture.notePatch("p-1", "cash_flow", firstId, first.updatedAt, "一后"),
                fixture.notePatch("p-2", "cash_flow", secondId, second.updatedAt, "二后"),
            ),
        )

        // The user then edits one of the batch-touched records outside the batch.
        val drifted = assertNotNull(fixture.transactions.queryCashFlowRecordById(secondId))
        fixture.transactions.updateCashFlowRecord(
            drifted.copy(note = "用户又改了", updatedAt = drifted.updatedAt + 1),
            drifted.updatedAt,
        )

        val result = fixture.journaled.undoLatest("undo-batch")
        val conflict = assertIs<UndoLatestAiMutationResult.BatchConflict>(result)
        assertEquals(2, conflict.items.size)
        assertTrue(conflict.items.none { it.restored })
        val driftedItem = conflict.items.single { it.recordId == secondId }
        assertNotNull(driftedItem.message)
        assertNull(conflict.items.single { it.recordId == firstId }.message)

        // All-or-nothing: neither record was restored and the entry stays applied.
        assertEquals("一后", fixture.transactions.queryCashFlowRecordById(firstId)?.note)
        assertEquals("用户又改了", fixture.transactions.queryCashFlowRecordById(secondId)?.note)
        val entry = assertNotNull(fixture.journal.queryByRequestId("push-batch"))
        assertEquals(AiMutationJournalStatus.APPLIED, entry.status)
    }

    @Test
    fun singleAndBatchEntriesShareOneStrictLifoStack() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId, note = "初始")
        fixture.journaled.createCashFlow(
            AiCreateCashFlowCommand(
                identity = fixture.identity("ai-create"),
                accountId = accountId,
                direction = CashFlowDirection.INFLOW,
                amount = 42,
                note = "AI 新增",
                occurredAt = 10,
            ),
        )
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))
        fixture.push(
            fixture.identity("push-batch"),
            "ds-push",
            listOf(fixture.notePatch("p-1", "cash_flow", recordId, stored.updatedAt, "批量备注")),
        )

        // Stack top is the batch — it pops first, then the single create, then the stack is empty.
        assertIs<UndoLatestAiMutationResult.BatchUndone>(fixture.journaled.undoLatest("u-1"))
        val single = assertIs<UndoLatestAiMutationResult.Undone>(fixture.journaled.undoLatest("u-2"))
        assertEquals("ai-create", single.entry.requestId)
        assertEquals(
            UndoLatestAiMutationResult.Empty,
            fixture.journaled.undoLatest("u-3"),
        )
    }
}
