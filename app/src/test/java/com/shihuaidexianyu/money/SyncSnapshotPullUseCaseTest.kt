package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemorySyncRepository
import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncResyncRequiredException
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotSourceRow
import com.shihuaidexianyu.money.domain.usecase.sync.ExportSyncSnapshotPageUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.GetSyncStateUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.PullSyncChangesUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Snapshot paging and pull catch-up semantics (design 4.4/4.5): pinned snapshotRevision, opaque
 * keyset cursors, byte-budgeted pages, retention-window RESYNC_REQUIRED, dataset mismatch.
 */
class SyncSnapshotPullUseCaseTest {
    private class Fixture {
        val clock = MutableClock(1_000L)
        val syncRepository = InMemorySyncRepository(
            datasetIdGenerator = { "ds-fixture" },
            createdAtProvider = { 0L },
        )
        val getState = GetSyncStateUseCase(syncRepository, clock)
        val snapshot = ExportSyncSnapshotPageUseCase(syncRepository)
        val pull = PullSyncChangesUseCase(syncRepository)

        suspend fun appendUpsert(kind: SyncEntityKind, recordId: Long, payload: String = "{\"k\":1}"): Long =
            syncRepository.appendChange(
                NewSyncChange(
                    entityKind = kind,
                    recordId = recordId,
                    operation = SyncChangeOperation.UPSERT,
                    payloadJson = payload,
                    updatedAt = 10L,
                ),
            )

        suspend fun appendDelete(kind: SyncEntityKind, recordId: Long): Long =
            syncRepository.appendChange(
                NewSyncChange(
                    entityKind = kind,
                    recordId = recordId,
                    operation = SyncChangeOperation.DELETE,
                    updatedAt = 20L,
                    deletedAt = 20L,
                ),
            )

        fun cashFlowRows(rows: List<SyncSnapshotSourceRow>) {
            syncRepository.snapshotProvider = { kind, afterId, limit ->
                if (kind != SyncEntityKind.CASH_FLOW) {
                    emptyList()
                } else {
                    rows.filter { it.recordId > afterId }.sortedBy { it.recordId }.take(limit)
                }
            }
        }
    }

    private class MutableClock(var now: Long) : com.shihuaidexianyu.money.domain.time.ClockProvider {
        override fun nowMillis(): Long = now
    }

    @Test
    fun stateReportsDatasetRevisionAndRetentionWindow() = runBlocking {
        val fixture = Fixture()
        val empty = fixture.getState()
        assertEquals("ds-fixture", empty.datasetId)
        assertEquals(0L, empty.revision)
        assertEquals(1L, empty.minAvailableRevision)
        assertEquals(1_000L, empty.serverTime)

        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 1)
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 2)
        val state = fixture.getState()
        assertEquals(2L, state.revision)
        assertEquals(1L, state.minAvailableRevision)

        fixture.syncRepository.pruneBefore(2)
        assertEquals(2L, fixture.getState().minAvailableRevision)
    }

    @Test
    fun pullReturnsAscendingChangesWithHasMoreAndCursorWindow() = runBlocking {
        val fixture = Fixture()
        repeat(5) { index -> fixture.appendUpsert(SyncEntityKind.CASH_FLOW, index.toLong() + 1) }

        val first = fixture.pull("ds-fixture", afterRevision = 0, limit = 2)
        assertEquals(listOf(1L, 2L), first.changes.map { it.revision })
        assertEquals(0L, first.fromRevision)
        assertEquals(2L, first.toRevision)
        assertTrue(first.hasMore)

        val second = fixture.pull("ds-fixture", afterRevision = first.toRevision, limit = 2)
        assertEquals(listOf(3L, 4L), second.changes.map { it.revision })
        assertTrue(second.hasMore)

        val last = fixture.pull("ds-fixture", afterRevision = second.toRevision, limit = 2)
        assertEquals(listOf(5L), last.changes.map { it.revision })
        assertEquals(5L, last.toRevision)
        assertFalse(last.hasMore)

        // An empty tail keeps the cursor stable.
        val tail = fixture.pull("ds-fixture", afterRevision = 5, limit = 2)
        assertEquals(5L, tail.toRevision)
        assertTrue(tail.changes.isEmpty())
        assertFalse(tail.hasMore)
    }

    @Test
    fun pullIncludesTombstonesWithDeletedAt() = runBlocking {
        val fixture = Fixture()
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 1)
        fixture.appendDelete(SyncEntityKind.CASH_FLOW, 1)

        val page = fixture.pull("ds-fixture", afterRevision = 0, limit = 10)
        assertEquals(2, page.changes.size)
        val tombstone = page.changes[1]
        assertEquals(SyncChangeOperation.DELETE, tombstone.operation)
        assertNull(tombstone.payloadJson)
        assertEquals(20L, tombstone.deletedAt)
    }

    @Test
    fun pullRejectsForeignDatasetAndPrunedCursor() = runBlocking {
        val fixture = Fixture()
        repeat(3) { index -> fixture.appendUpsert(SyncEntityKind.CASH_FLOW, index.toLong() + 1) }

        assertFailsWith<SyncDatasetMismatchException> {
            fixture.pull("ds-other", afterRevision = 0, limit = 10)
        }

        fixture.syncRepository.pruneBefore(3)
        // Cursor 0/1 are before the retained window (minAvailable = 3).
        assertFailsWith<SyncResyncRequiredException> {
            fixture.pull("ds-fixture", afterRevision = 0, limit = 10)
        }
        assertFailsWith<SyncResyncRequiredException> {
            fixture.pull("ds-fixture", afterRevision = 1, limit = 10)
        }
        // Cursor 2 is exactly at the window edge and remains valid.
        val page = fixture.pull("ds-fixture", afterRevision = 2, limit = 10)
        assertEquals(listOf(3L), page.changes.map { it.revision })
    }

    @Test
    fun snapshotPinsRevisionIncludesSourceRevisionAndTombstones() = runBlocking {
        val fixture = Fixture()
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 1)
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 2)
        fixture.cashFlowRows(
            listOf(
                SyncSnapshotSourceRow(recordId = 1, payloadJson = "{\"recordId\":1}", updatedAt = 10, deletedAt = null),
                SyncSnapshotSourceRow(recordId = 2, payloadJson = "{\"recordId\":2}", updatedAt = 20, deletedAt = 30),
                SyncSnapshotSourceRow(recordId = 3, payloadJson = "{\"recordId\":3}", updatedAt = 40, deletedAt = null),
            ),
        )

        val page = fixture.snapshot("ds-fixture")

        assertEquals("ds-fixture", page.datasetId)
        assertEquals(2L, page.snapshotRevision)
        assertTrue(page.done)
        assertNull(page.nextCursor)
        assertEquals(3, page.rows.size)
        // sourceRevision comes from the change-log; row 3 was never logged.
        assertEquals(listOf(1L, 2L, 0L), page.rows.map { it.sourceRevision })
        // The soft-deleted row 2 surfaces as a tombstone without payload.
        assertNull(page.rows[1].payloadJson)
        assertEquals(30L, page.rows[1].deletedAt)
        assertEquals(20L, page.rows[1].updatedAt)
    }

    @Test
    fun snapshotSplitsPagesOnByteBudgetAndResumesFromCursor() = runBlocking {
        val fixture = Fixture()
        // 60KiB payloads (~61.6KB/row with envelope): two rows per 128KiB page, six rows, 3 pages.
        val fatPayload = "{\"note\":\"" + "x".repeat(60 * 1024) + "\"}"
        fixture.cashFlowRows(
            (1L..6L).map { id ->
                SyncSnapshotSourceRow(recordId = id, payloadJson = fatPayload, updatedAt = id, deletedAt = null)
            },
        )

        val seen = mutableListOf<Long>()
        var cursor: String? = null
        var snapshotRevision: Long? = null
        var pages = 0
        while (true) {
            val page = fixture.snapshot("ds-fixture", snapshotRevision = snapshotRevision, cursor = cursor)
            pages += 1
            snapshotRevision = page.snapshotRevision
            seen += page.rows.map { it.recordId }
            assertTrue(page.rows.isNotEmpty())
            if (page.done) {
                assertNull(page.nextCursor)
                break
            }
            cursor = assertNotNull(page.nextCursor)
            check(pages < 20) { "paging did not converge" }
        }

        assertEquals(3, pages)
        assertEquals((1L..6L).toList(), seen)
    }

    @Test
    fun snapshotResumeRequiresMatchingPinnedRevisionAndLiveWindow() = runBlocking {
        val fixture = Fixture()
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 1)
        val fatPayload = "{\"note\":\"" + "x".repeat(100 * 1024) + "\"}"
        fixture.cashFlowRows(
            (1L..3L).map { id ->
                SyncSnapshotSourceRow(recordId = id, payloadJson = fatPayload, updatedAt = id, deletedAt = null)
            },
        )

        val first = fixture.snapshot("ds-fixture")
        assertFalse(first.done)
        val cursor = assertNotNull(first.nextCursor)

        // A cursor carries its pinned snapshotRevision; a mismatched one is rejected.
        assertFailsWith<IllegalArgumentException> {
            fixture.snapshot("ds-fixture", snapshotRevision = first.snapshotRevision + 1, cursor = cursor)
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.snapshot("ds-fixture", snapshotRevision = null, cursor = cursor)
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.snapshot("ds-fixture", snapshotRevision = first.snapshotRevision, cursor = "not-a-cursor")
        }

        // Retention pruning past the pinned revision forces a rebase.
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 98)
        fixture.appendUpsert(SyncEntityKind.CASH_FLOW, 99)
        fixture.syncRepository.pruneBefore(3)
        assertFailsWith<SyncResyncRequiredException> {
            fixture.snapshot("ds-fixture", snapshotRevision = first.snapshotRevision, cursor = cursor)
        }
        Unit
    }

    @Test
    fun snapshotRejectsForeignDataset() = runBlocking {
        val fixture = Fixture()
        assertFailsWith<SyncDatasetMismatchException> {
            fixture.snapshot("ds-other")
        }
        Unit
    }
}
