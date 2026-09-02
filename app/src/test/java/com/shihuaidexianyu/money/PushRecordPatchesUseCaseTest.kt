package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationAction
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.model.sync.RecordPatch
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncPatchStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Contract tests for sync.push.records.v1: mixed create/update/delete batches over the same
 * in-memory fixture as the note pipeline. Pins per-patch validation, idempotent replay, the
 * zero-applied no-journal rule, and the three-way batch undo (create→delete, delete→restore,
 * update→snapshot restore).
 */
class PushRecordPatchesUseCaseTest {

    @Test
    fun mixedBatchAppliesAndJournalsPerItemSnapshots() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val bankId = fixture.newAccount("招行")
        val cashId = fixture.newCashFlow(accountId, note = "原备注")
        val transferId = fixture.newTransfer(accountId, bankId)
        val cash = assertNotNull(fixture.transactions.queryCashFlowRecordById(cashId))
        val transfer = assertNotNull(fixture.transactions.queryTransferRecordById(transferId))

        val result = fixture.pushRecords(
            fixture.identity("rec-1"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                RecordPatch(
                    patchId = "p-create",
                    op = "create",
                    entityKind = "cash_flow",
                    changes = buildJsonObject {
                        put("accountId", JsonPrimitive(accountId))
                        put("direction", JsonPrimitive("inflow"))
                        put("amount", JsonPrimitive("1288"))
                        put("note", JsonPrimitive("  新收入  "))
                        put("occurredAt", JsonPrimitive(10L))
                    },
                ),
                RecordPatch(
                    patchId = "p-update",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = cashId,
                    expectedUpdatedAt = cash.updatedAt,
                    changes = buildJsonObject { put("note", JsonPrimitive("改后")) },
                ),
                RecordPatch(
                    patchId = "p-delete",
                    op = "delete",
                    entityKind = "transfer",
                    recordId = transferId,
                    expectedUpdatedAt = transfer.updatedAt,
                    changes = JsonObject(emptyMap()),
                ),
            ),
        )

        assertFalse(result.replayed)
        assertEquals(
            listOf(SyncPatchStatus.APPLIED, SyncPatchStatus.APPLIED, SyncPatchStatus.APPLIED),
            result.results.map { it.status },
        )
        val createdId = assertNotNull(result.results[0].recordId)
        assertTrue(createdId > 0L)
        val created = assertNotNull(fixture.transactions.queryCashFlowRecordById(createdId))
        assertEquals("新收入", created.note)
        assertEquals(1288L, created.amount)
        assertEquals("ai:rec-1:p-create", created.operationId)
        assertEquals("改后", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertNotNull(fixture.transactions.queryStoredTransferRecordById(transferId)?.deletedAt)

        val entry = fixture.journal.queryRecent(10).single()
        assertEquals(AiMutationEntryType.BATCH, entry.entryType)
        assertEquals(AiMutationAction.BATCH_RECORD_WRITE, entry.action)
        assertEquals(3, entry.itemCount)
        assertEquals(3, entry.appliedCount)
        assertEquals(0, entry.conflictCount)

        val items = fixture.journal.queryItems(entry.id)
        assertEquals(3, items.size)
        val json = Json { ignoreUnknownKeys = true }
        // create: no before snapshot, after snapshot carries the new record.
        assertNull(items[0].beforeSnapshotJson)
        val createdAfter = json.decodeFromString(
            AiLedgerRecordSnapshot.serializer(),
            assertNotNull(items[0].afterSnapshotJson),
        )
        assertEquals(createdId, createdAfter.recordId)
        assertEquals(createdId, items[0].recordId)
        // update: both snapshots bracket the change.
        val updateBefore = json.decodeFromString(
            AiLedgerRecordSnapshot.serializer(),
            assertNotNull(items[1].beforeSnapshotJson),
        )
        assertEquals("原备注", updateBefore.note)
        // delete: after snapshot is the soft-deleted row.
        val deleteAfter = json.decodeFromString(
            AiLedgerRecordSnapshot.serializer(),
            assertNotNull(items[2].afterSnapshotJson),
        )
        assertNotNull(deleteAfter.deletedAt)
        assertEquals(transferId, items[2].recordId)
    }

    @Test
    fun createValidationErrorsAreInvalidButNeverFailTheBatch() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")

        fun createPatch(patchId: String, changes: JsonObject, recordId: Long? = null) = RecordPatch(
            patchId = patchId,
            op = "create",
            entityKind = "cash_flow",
            recordId = recordId,
            changes = changes,
        )

        val result = fixture.pushRecords(
            fixture.identity("rec-invalid"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                createPatch("p-id", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("outflow"))
                    put("amount", JsonPrimitive("100"))
                }, recordId = 42L),
                createPatch("p-zero", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("outflow"))
                    put("amount", JsonPrimitive("0"))
                }),
                createPatch("p-nan", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("outflow"))
                    put("amount", JsonPrimitive("abc"))
                }),
                createPatch("p-dir", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("sideways"))
                    put("amount", JsonPrimitive("100"))
                }),
                createPatch("p-field", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("outflow"))
                    put("amount", JsonPrimitive("100"))
                    put("surprise", JsonPrimitive("x"))
                }),
                createPatch("p-ok", buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("outflow"))
                    put("amount", JsonPrimitive("100"))
                }),
            ),
        )

        assertEquals(
            listOf(
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.INVALID,
                SyncPatchStatus.APPLIED,
            ),
            result.results.map { it.status },
        )
        assertTrue(result.results.take(5).all { it.errorCode == "INVALID_PATCH" })
        val entry = fixture.journal.queryRecent(10).single()
        assertEquals(6, entry.itemCount)
        assertEquals(1, entry.appliedCount)
    }

    @Test
    fun duplicatePatchIdsWithinOneBatchAreInvalid() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val changes = buildJsonObject {
            put("accountId", JsonPrimitive(accountId))
            put("direction", JsonPrimitive("outflow"))
            put("amount", JsonPrimitive("100"))
        }

        val result = fixture.pushRecords(
            fixture.identity("rec-dup"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                RecordPatch(patchId = "p-same", op = "create", entityKind = "cash_flow", changes = changes),
                RecordPatch(patchId = "p-same", op = "create", entityKind = "cash_flow", changes = changes),
            ),
        )

        assertEquals(
            listOf(SyncPatchStatus.APPLIED, SyncPatchStatus.INVALID),
            result.results.map { it.status },
        )
        assertEquals("patchId 重复", result.results[1].errorMessage)
        // Exactly one record was created.
        assertEquals(1, fixture.transactions.queryAllActiveCashFlowRecords().size)
    }

    @Test
    fun staleUpdateAndDeleteConflictWithServerPayload() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId, note = "服务器备注")
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))

        val result = fixture.pushRecords(
            fixture.identity("rec-conflict"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                RecordPatch(
                    patchId = "p-stale-update",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    expectedUpdatedAt = stored.updatedAt + 999,
                    changes = buildJsonObject { put("note", JsonPrimitive("客户端备注")) },
                ),
                RecordPatch(
                    patchId = "p-stale-delete",
                    op = "delete",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    expectedUpdatedAt = stored.updatedAt + 999,
                    changes = JsonObject(emptyMap()),
                ),
            ),
        )

        assertEquals(
            listOf(SyncPatchStatus.CONFLICT, SyncPatchStatus.CONFLICT),
            result.results.map { it.status },
        )
        result.results.forEach { patch ->
            assertEquals(stored.updatedAt, patch.serverUpdatedAt)
            val payload = Json.parseToJsonElement(assertNotNull(patch.serverPayloadJson)).jsonObject
            assertEquals("服务器备注", payload.getValue("note").jsonPrimitive.content)
        }
        // Nothing mutated, nothing journaled (zero applied).
        assertEquals("服务器备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        assertTrue(fixture.journal.queryRecent(10).isEmpty())
    }

    @Test
    fun updateAndDeleteShapeErrorsAreInvalid() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val recordId = fixture.newCashFlow(accountId)
        val stored = assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))

        val result = fixture.pushRecords(
            fixture.identity("rec-shape"),
            expectedDatasetId = "ds-push",
            patches = listOf(
                // update without expectedUpdatedAt.
                RecordPatch(
                    patchId = "p-no-cas",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    changes = buildJsonObject { put("note", JsonPrimitive("n")) },
                ),
                // update with empty changes.
                RecordPatch(
                    patchId = "p-empty",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    expectedUpdatedAt = stored.updatedAt,
                    changes = JsonObject(emptyMap()),
                ),
                // update with an invalid direction must not be silently ignored.
                RecordPatch(
                    patchId = "p-bad-dir",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    expectedUpdatedAt = stored.updatedAt,
                    changes = buildJsonObject { put("direction", JsonPrimitive("sideways")) },
                ),
                // delete carrying changes.
                RecordPatch(
                    patchId = "p-del-changes",
                    op = "delete",
                    entityKind = "cash_flow",
                    recordId = recordId,
                    expectedUpdatedAt = stored.updatedAt,
                    changes = buildJsonObject { put("note", JsonPrimitive("n")) },
                ),
                // delete of a missing record.
                RecordPatch(
                    patchId = "p-del-missing",
                    op = "delete",
                    entityKind = "cash_flow",
                    recordId = 999_999,
                    expectedUpdatedAt = 1,
                    changes = JsonObject(emptyMap()),
                ),
                // patch without op (the router only sends note-shaped patches to the note pipeline,
                // but a direct call must still be classified, never crash).
                RecordPatch(patchId = "p-no-op", entityKind = "cash_flow", changes = JsonObject(emptyMap())),
            ),
        )

        assertEquals(
            (1..6).map { SyncPatchStatus.INVALID },
            result.results.map { it.status },
        )
        // Zero applied: no journal entry, no mutation.
        assertTrue(fixture.journal.queryRecent(10).isEmpty())
        assertEquals("原备注", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
    }

    @Test
    fun replayReturnsStoredResultsIncludingCreatedRecordIds() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val patches = listOf(
            RecordPatch(
                patchId = "p-create",
                op = "create",
                entityKind = "cash_flow",
                changes = buildJsonObject {
                    put("accountId", JsonPrimitive(accountId))
                    put("direction", JsonPrimitive("inflow"))
                    put("amount", JsonPrimitive("500"))
                },
            ),
        )

        val first = fixture.pushRecords(fixture.identity("rec-replay"), "ds-push", patches)
        val revisionAfterFirst = fixture.syncRepository.readDatasetState().currentRevision
        val replay = fixture.pushRecords(fixture.identity("rec-replay"), "ds-push", patches)

        assertFalse(first.replayed)
        assertTrue(replay.replayed)
        assertEquals(first.results, replay.results)
        assertNotNull(replay.results.single().recordId)
        // No second journal entry, no second record, no new change-log revision.
        assertEquals(1, fixture.journal.queryRecent(10).size)
        assertEquals(1, fixture.transactions.queryAllActiveCashFlowRecords().size)
        assertEquals(revisionAfterFirst, fixture.syncRepository.readDatasetState().currentRevision)
        assertEquals(first.results, fixture.pushRecords.findStoredResults("rec-replay"))
        assertNull(fixture.pushRecords.findStoredResults("rec-unknown"))
    }

    @Test
    fun datasetMismatchRejectsTheWholeBatch() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")

        assertFailsWith<SyncDatasetMismatchException> {
            fixture.pushRecords(
                fixture.identity("rec-mismatch"),
                expectedDatasetId = "ds-other",
                patches = listOf(
                    RecordPatch(
                        patchId = "p-1",
                        op = "create",
                        entityKind = "cash_flow",
                        changes = buildJsonObject {
                            put("accountId", JsonPrimitive(accountId))
                            put("direction", JsonPrimitive("outflow"))
                            put("amount", JsonPrimitive("100"))
                        },
                    ),
                ),
            )
        }
        assertTrue(fixture.transactions.queryAllActiveCashFlowRecords().isEmpty())
        assertTrue(fixture.journal.queryRecent(10).isEmpty())
    }
}

class BatchRecordUndoTest {

    @Test
    fun undoDeletesCreatedRestoresDeletedAndRevertsUpdates() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val bankId = fixture.newAccount("招行")
        val cashId = fixture.newCashFlow(accountId, note = "饭前")
        val transferId = fixture.newTransfer(accountId, bankId, note = "转账备注")
        val cash = assertNotNull(fixture.transactions.queryCashFlowRecordById(cashId))
        val transfer = assertNotNull(fixture.transactions.queryTransferRecordById(transferId))

        val result = fixture.pushRecords(
            fixture.identity("rec-batch"),
            "ds-push",
            listOf(
                RecordPatch(
                    patchId = "p-create",
                    op = "create",
                    entityKind = "cash_flow",
                    recordId = null,
                    expectedUpdatedAt = null,
                    changes = buildJsonObject {
                        put("accountId", JsonPrimitive(accountId))
                        put("direction", JsonPrimitive("outflow"))
                        put("amount", JsonPrimitive("777"))
                        put("note", JsonPrimitive("AI 新增"))
                        put("occurredAt", JsonPrimitive(10L))
                    },
                ),
                RecordPatch(
                    patchId = "p-update",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = cashId,
                    expectedUpdatedAt = cash.updatedAt,
                    changes = buildJsonObject { put("note", JsonPrimitive("饭后")) },
                ),
                RecordPatch(
                    patchId = "p-delete",
                    op = "delete",
                    entityKind = "transfer",
                    recordId = transferId,
                    expectedUpdatedAt = transfer.updatedAt,
                    changes = JsonObject(emptyMap()),
                ),
            ),
        )
        val createdId = assertNotNull(result.results[0].recordId)
        assertEquals("饭后", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertNull(fixture.transactions.queryTransferRecordById(transferId))

        val undone = fixture.journaled.undoLatest("undo-rec")
        val batch = assertIs<UndoLatestAiMutationResult.BatchUndone>(undone)
        assertFalse(batch.replayed)
        assertEquals(3, batch.items.size)
        assertTrue(batch.items.all { it.restored })

        // create undone: the new record is soft-deleted.
        assertNull(fixture.transactions.queryCashFlowRecordById(createdId))
        assertNotNull(fixture.transactions.queryStoredCashFlowRecordById(createdId)?.deletedAt)
        // update undone: the note is back.
        assertEquals("饭前", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        // delete undone: the transfer is active again with its content intact.
        val restoredTransfer = assertNotNull(fixture.transactions.queryTransferRecordById(transferId))
        assertEquals("转账备注", restoredTransfer.note)
        assertNull(restoredTransfer.deletedAt)

        val entry = assertNotNull(fixture.journal.queryByRequestId("rec-batch"))
        assertEquals(AiMutationJournalStatus.UNDONE, entry.status)
        assertTrue(fixture.journal.queryItems(entry.id).all { it.undoneAt != null })

        // Replayed undo is side-effect free and returns the same batch shape.
        val replayed = fixture.journaled.undoLatest("undo-rec")
        val replayedBatch = assertIs<UndoLatestAiMutationResult.BatchUndone>(replayed)
        assertTrue(replayedBatch.replayed)
        assertEquals(batch.items, replayedBatch.items)
        assertEquals("饭前", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertNull(fixture.transactions.queryCashFlowRecordById(createdId))
    }

    @Test
    fun driftInACreatedItemRejectsTheWholeBatchUndo() = runBlocking {
        val fixture = SyncPushFixture()
        val accountId = fixture.newAccount("现金")
        val cashId = fixture.newCashFlow(accountId, note = "饭前")
        val cash = assertNotNull(fixture.transactions.queryCashFlowRecordById(cashId))

        val result = fixture.pushRecords(
            fixture.identity("rec-drift"),
            "ds-push",
            listOf(
                RecordPatch(
                    patchId = "p-create",
                    op = "create",
                    entityKind = "cash_flow",
                    recordId = null,
                    expectedUpdatedAt = null,
                    changes = buildJsonObject {
                        put("accountId", JsonPrimitive(accountId))
                        put("direction", JsonPrimitive("outflow"))
                        put("amount", JsonPrimitive("100"))
                        put("note", JsonPrimitive("AI 新增"))
                    },
                ),
                RecordPatch(
                    patchId = "p-update",
                    op = "update",
                    entityKind = "cash_flow",
                    recordId = cashId,
                    expectedUpdatedAt = cash.updatedAt,
                    changes = buildJsonObject { put("note", JsonPrimitive("饭后")) },
                ),
            ),
        )
        val createdId = assertNotNull(result.results[0].recordId)

        // The user edits the created record outside the batch.
        val created = assertNotNull(fixture.transactions.queryCashFlowRecordById(createdId))
        fixture.transactions.updateCashFlowRecord(
            created.copy(note = "用户又改了", updatedAt = created.updatedAt + 1),
            created.updatedAt,
        )

        val undo = fixture.journaled.undoLatest("undo-drift")
        val conflict = assertIs<UndoLatestAiMutationResult.BatchConflict>(undo)
        assertEquals(2, conflict.items.size)
        assertTrue(conflict.items.none { it.restored })
        assertNotNull(conflict.items.single { it.recordId == createdId }.message)
        assertNull(conflict.items.single { it.recordId == cashId }.message)

        // All-or-nothing: the update is NOT reverted and the user's edit survives.
        assertEquals("饭后", fixture.transactions.queryCashFlowRecordById(cashId)?.note)
        assertEquals("用户又改了", fixture.transactions.queryCashFlowRecordById(createdId)?.note)
        val entry = assertNotNull(fixture.journal.queryByRequestId("rec-drift"))
        assertEquals(AiMutationJournalStatus.APPLIED, entry.status)
    }
}
