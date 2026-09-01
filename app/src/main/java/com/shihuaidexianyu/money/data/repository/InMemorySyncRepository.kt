package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetState
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotSourceRow
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hermetic [SyncRepository] for unit tests. The change-log and dataset state behave exactly like
 * the Room implementation; snapshot rows are delegated to [snapshotProvider] so tests can back
 * them with the other in-memory repositories' stores.
 */
class InMemorySyncRepository(
    private val datasetIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val createdAtProvider: () -> Long = { System.currentTimeMillis() },
) : SyncRepository {
    /** (entityKind, afterRecordId, limit) -> keyset page of current-projection source rows. */
    var snapshotProvider: (SyncEntityKind, Long, Int) -> List<SyncSnapshotSourceRow> =
        { _, _, _ -> emptyList() }

    private val mutex = Mutex()
    private var datasetState: SyncDatasetState? = null
    private val changes = mutableListOf<SyncChange>()

    override suspend fun readDatasetState(): SyncDatasetState = mutex.withLock {
        datasetState ?: SyncDatasetState(
            datasetId = datasetIdGenerator(),
            nextRevision = 1L,
            createdAt = createdAtProvider(),
        ).also { datasetState = it }
    }

    override suspend fun appendChange(change: NewSyncChange): Long = mutex.withLock {
        val state = readDatasetStateLocked()
        val revision = state.nextRevision
        changes += SyncChange(
            revision = revision,
            entityKind = change.entityKind,
            recordId = change.recordId,
            operation = change.operation,
            payloadJson = change.payloadJson,
            updatedAt = change.updatedAt,
            deletedAt = change.deletedAt,
            requestId = change.requestId,
        )
        datasetState = state.copy(nextRevision = revision + 1L)
        revision
    }

    override suspend fun queryLatestRevisionFor(entityKind: SyncEntityKind, recordId: Long): Long? =
        mutex.withLock {
            changes.filter { it.entityKind == entityKind && it.recordId == recordId }
                .maxOfOrNull { it.revision }
        }

    override suspend fun queryChangesAfter(afterRevision: Long, limit: Int): List<SyncChange> =
        mutex.withLock {
            changes.filter { it.revision > afterRevision }
                .sortedBy { it.revision }
                .take(limit)
        }

    override suspend fun minLoggedRevision(): Long? = mutex.withLock {
        changes.minOfOrNull { it.revision }
    }

    override suspend fun resetDataset(newDatasetId: String, createdAt: Long): Unit = mutex.withLock {
        require(newDatasetId.isNotBlank()) { "datasetId 不能为空" }
        changes.clear()
        datasetState = SyncDatasetState(
            datasetId = newDatasetId,
            nextRevision = 1L,
            createdAt = createdAt,
        )
    }

    override suspend fun querySnapshotRows(
        entityKind: SyncEntityKind,
        afterRecordId: Long,
        limit: Int,
    ): List<SyncSnapshotSourceRow> = snapshotProvider(entityKind, afterRecordId, limit)

    override suspend fun queryLatestRevisions(
        entityKind: SyncEntityKind,
        recordIds: List<Long>,
    ): Map<Long, Long> = mutex.withLock {
        if (recordIds.isEmpty()) return@withLock emptyMap()
        val wanted = recordIds.toSet()
        changes.filter { it.entityKind == entityKind && it.recordId in wanted }
            .groupBy { it.recordId }
            .mapValues { (_, rows) -> rows.maxOf { it.revision } }
    }

    /** Test support: drops change-log rows below [revision], simulating retention pruning. */
    suspend fun pruneBefore(revision: Long): Unit = mutex.withLock {
        changes.removeAll { it.revision < revision }
    }

    private fun readDatasetStateLocked(): SyncDatasetState =
        datasetState ?: SyncDatasetState(
            datasetId = datasetIdGenerator(),
            nextRevision = 1L,
            createdAt = createdAtProvider(),
        ).also { datasetState = it }
}
