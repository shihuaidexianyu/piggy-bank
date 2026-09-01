package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetState
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotSourceRow

/**
 * Port for the sync v1 persistent dataset state and change-log.
 *
 * All mutating methods must be called INSIDE the caller's ledger transaction (the same Room
 * transaction as the mutation they describe) so a ledger change and its change-log entry are
 * always committed or rolled back together.
 */
interface SyncRepository {
    /** Reads the singleton dataset row, creating it (fresh datasetId, nextRevision = 1) if absent. */
    suspend fun readDatasetState(): SyncDatasetState

    /** Appends one change, allocating its revision from `sync_dataset.nextRevision`. */
    suspend fun appendChange(change: NewSyncChange): Long

    /** Latest allocated change-log revision for one row, or null when it was never logged. */
    suspend fun queryLatestRevisionFor(entityKind: SyncEntityKind, recordId: Long): Long?

    /** Change entries with `revision > afterRevision`, ascending, at most [limit] rows. */
    suspend fun queryChangesAfter(afterRevision: Long, limit: Int): List<SyncChange>

    /** Smallest revision still present in the change-log, or null when the log is empty. */
    suspend fun minLoggedRevision(): Long?

    /**
     * Dataset switch on backup replace/rollback: clears the change-log and starts a new
     * datasetId with nextRevision = 1. Called inside the backup replacement transaction.
     */
    suspend fun resetDataset(newDatasetId: String, createdAt: Long)

    /**
     * Keyset page over the CURRENT projection of one entity kind (including soft-deleted rows,
     * which surface as tombstones), ordered by record id, strictly after [afterRecordId].
     */
    suspend fun querySnapshotRows(
        entityKind: SyncEntityKind,
        afterRecordId: Long,
        limit: Int,
    ): List<SyncSnapshotSourceRow>

    /** Max change-log revision per row (0 when absent) for snapshot `sourceRevision` enrichment. */
    suspend fun queryLatestRevisions(
        entityKind: SyncEntityKind,
        recordIds: List<Long>,
    ): Map<Long, Long>
}
