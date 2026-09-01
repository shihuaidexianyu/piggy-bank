package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.AiMutationJournalDao
import com.shihuaidexianyu.money.data.dao.AiMutationJournalItemDao
import com.shihuaidexianyu.money.data.entity.AiMutationJournalEntity
import com.shihuaidexianyu.money.data.entity.AiMutationJournalItemEntity
import com.shihuaidexianyu.money.domain.model.AiMutationAction
import com.shihuaidexianyu.money.domain.model.AiMutationEntryType
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiMutationJournalRepositoryImpl(
    private val dao: AiMutationJournalDao,
    private val itemDao: AiMutationJournalItemDao,
    private val clockProvider: ClockProvider,
) : AiMutationJournalRepository {
    override fun observeRecent(limit: Int): Flow<List<AiMutationJournalEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map(AiMutationJournalEntity::toDomain) }

    override fun observeAppliedCount(): Flow<Int> = dao.observeAppliedCount()

    override fun observeLatestApplied(): Flow<AiMutationJournalEntry?> =
        dao.observeLatestApplied().map { it?.toDomain() }

    override suspend fun queryRecent(limit: Int): List<AiMutationJournalEntry> =
        dao.queryRecent(limit).map(AiMutationJournalEntity::toDomain)

    override suspend fun queryByRequestId(requestId: String): AiMutationJournalEntry? =
        dao.queryByRequestId(requestId)?.toDomain()

    override suspend fun queryByUndoRequestId(requestId: String): AiMutationJournalEntry? =
        dao.queryByUndoRequestId(requestId)?.toDomain()

    override suspend fun queryLatestApplied(): AiMutationJournalEntry? =
        dao.queryLatestApplied()?.toDomain()

    override suspend fun insert(entry: AiMutationJournalEntry): Long = dao.insert(entry.toEntity())

    override suspend fun insertBatch(
        entry: AiMutationJournalEntry,
        items: List<AiMutationJournalItem>,
    ): Long {
        val entryId = dao.insert(entry.toEntity())
        itemDao.insertAll(items.map { it.toEntity(entryId) })
        return entryId
    }

    override suspend fun queryItems(journalId: Long): List<AiMutationJournalItem> =
        itemDao.queryItems(journalId).map(AiMutationJournalItemEntity::toDomain)

    override suspend fun markItemsUndone(journalId: Long) {
        itemDao.markUndone(journalId, clockProvider.nowMillis())
    }

    override suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Boolean =
        dao.markUndone(id, undoneAt, undoRequestId) == 1

    override suspend fun discardLatest(id: Long, discardedAt: Long): Boolean =
        dao.discardLatest(id, discardedAt) == 1

    override suspend fun deleteAll() {
        itemDao.deleteAll()
        dao.deleteAll()
    }
}

private fun AiMutationJournalEntity.toDomain() = AiMutationJournalEntry(
    id = id,
    requestId = requestId,
    sessionId = sessionId,
    clientName = clientName,
    action = AiMutationAction.valueOf(action),
    recordKind = recordKind?.let(LedgerRecordKind::valueOf),
    recordId = recordId,
    summary = summary,
    beforeSnapshotJson = beforeSnapshotJson,
    afterSnapshotJson = afterSnapshotJson,
    undoTokenJson = undoTokenJson,
    status = AiMutationJournalStatus.fromValue(status),
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    undoRequestId = undoRequestId,
    entryType = AiMutationEntryType.fromValue(entryType),
    itemCount = itemCount,
    appliedCount = appliedCount,
    conflictCount = conflictCount,
)

private fun AiMutationJournalEntry.toEntity() = AiMutationJournalEntity(
    id = id,
    requestId = requestId,
    sessionId = sessionId,
    clientName = clientName,
    action = action.name,
    recordKind = recordKind?.name,
    recordId = recordId,
    summary = summary,
    beforeSnapshotJson = beforeSnapshotJson,
    afterSnapshotJson = afterSnapshotJson,
    undoTokenJson = undoTokenJson,
    status = status.value,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    undoRequestId = undoRequestId,
    entryType = entryType.value,
    itemCount = itemCount,
    appliedCount = appliedCount,
    conflictCount = conflictCount,
)

private fun AiMutationJournalItemEntity.toDomain() = AiMutationJournalItem(
    journalId = journalId,
    itemIndex = itemIndex,
    patchId = patchId,
    entityKind = entityKind,
    recordId = recordId,
    status = status,
    beforeSnapshotJson = beforeSnapshotJson,
    afterSnapshotJson = afterSnapshotJson,
    expectedUpdatedAt = expectedUpdatedAt,
    revision = revision,
    serverUpdatedAt = serverUpdatedAt,
    serverPayloadJson = serverPayloadJson,
    errorCode = errorCode,
    errorMessage = errorMessage,
    undoneAt = undoneAt,
)

private fun AiMutationJournalItem.toEntity(journalId: Long) = AiMutationJournalItemEntity(
    journalId = journalId,
    itemIndex = itemIndex,
    patchId = patchId,
    entityKind = entityKind,
    recordId = recordId,
    status = status,
    beforeSnapshotJson = beforeSnapshotJson,
    afterSnapshotJson = afterSnapshotJson,
    expectedUpdatedAt = expectedUpdatedAt,
    revision = revision,
    serverUpdatedAt = serverUpdatedAt,
    serverPayloadJson = serverPayloadJson,
    errorCode = errorCode,
    errorMessage = errorMessage,
    undoneAt = undoneAt,
)
