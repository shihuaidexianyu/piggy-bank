package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.SyncDao
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.SyncChangeLogEntity
import com.shihuaidexianyu.money.data.entity.SyncDatasetEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetState
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorPayloads
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotSourceRow
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.util.UUID

class SyncRepositoryImpl(
    private val syncDao: SyncDao,
    private val clockProvider: ClockProvider,
    private val datasetIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : SyncRepository {
    override suspend fun readDatasetState(): SyncDatasetState =
        syncDao.queryDataset()?.toDomain() ?: createDataset()

    private suspend fun createDataset(): SyncDatasetState {
        val entity = SyncDatasetEntity(
            datasetId = datasetIdGenerator(),
            nextRevision = 1L,
            createdAt = clockProvider.nowMillis(),
        )
        syncDao.upsertDataset(entity)
        return entity.toDomain()
    }

    override suspend fun appendChange(change: NewSyncChange): Long {
        // Revision allocation must run inside the caller's transaction together with the ledger
        // mutation: read nextRevision, insert the row, persist the incremented counter.
        val dataset = readDatasetState()
        val revision = dataset.nextRevision
        syncDao.insertChange(
            SyncChangeLogEntity(
                revision = revision,
                entityKind = change.entityKind.value,
                recordId = change.recordId,
                operation = change.operation.value,
                payloadJson = change.payloadJson,
                updatedAt = change.updatedAt,
                deletedAt = change.deletedAt,
                requestId = change.requestId,
                createdAt = clockProvider.nowMillis(),
            ),
        )
        syncDao.upsertDataset(
            SyncDatasetEntity(
                datasetId = dataset.datasetId,
                nextRevision = revision + 1L,
                createdAt = dataset.createdAt,
            ),
        )
        return revision
    }

    override suspend fun queryLatestRevisionFor(entityKind: SyncEntityKind, recordId: Long): Long? =
        syncDao.queryLatestRevisionFor(entityKind.value, recordId)

    override suspend fun queryChangesAfter(afterRevision: Long, limit: Int): List<SyncChange> =
        syncDao.queryChangesAfter(afterRevision, limit).map { entity ->
            SyncChange(
                revision = entity.revision,
                entityKind = requireNotNull(SyncEntityKind.fromValue(entity.entityKind)),
                recordId = entity.recordId,
                operation = SyncChangeOperation.fromValue(entity.operation),
                payloadJson = entity.payloadJson,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt,
                requestId = entity.requestId,
            )
        }

    override suspend fun minLoggedRevision(): Long? = syncDao.minLoggedRevision()

    override suspend fun resetDataset(newDatasetId: String, createdAt: Long) {
        require(newDatasetId.isNotBlank()) { "datasetId 不能为空" }
        syncDao.clearChangeLog()
        syncDao.upsertDataset(
            SyncDatasetEntity(
                datasetId = newDatasetId,
                nextRevision = 1L,
                createdAt = createdAt,
            ),
        )
    }

    override suspend fun querySnapshotRows(
        entityKind: SyncEntityKind,
        afterRecordId: Long,
        limit: Int,
    ): List<SyncSnapshotSourceRow> = when (entityKind) {
        SyncEntityKind.ACCOUNT -> syncDao.queryAccountSnapshotRows(afterRecordId, limit)
            .map { it.toSnapshotSourceRow() }
        SyncEntityKind.CASH_FLOW -> syncDao.queryCashFlowSnapshotRows(afterRecordId, limit)
            .map { it.toSnapshotSourceRow() }
        SyncEntityKind.TRANSFER -> syncDao.queryTransferSnapshotRows(afterRecordId, limit)
            .map { it.toSnapshotSourceRow() }
        SyncEntityKind.BALANCE_UPDATE -> syncDao.queryBalanceUpdateSnapshotRows(afterRecordId, limit)
            .map { it.toSnapshotSourceRow() }
        SyncEntityKind.BALANCE_ADJUSTMENT -> syncDao.queryBalanceAdjustmentSnapshotRows(afterRecordId, limit)
            .map { it.toSnapshotSourceRow() }
    }

    override suspend fun queryLatestRevisions(
        entityKind: SyncEntityKind,
        recordIds: List<Long>,
    ): Map<Long, Long> {
        if (recordIds.isEmpty()) return emptyMap()
        return syncDao.queryLatestRevisions(entityKind.value, recordIds)
            .associate { it.recordId to it.revision }
    }

    private fun SyncDatasetEntity.toDomain() = SyncDatasetState(
        datasetId = datasetId,
        nextRevision = nextRevision,
        createdAt = createdAt,
    )
}

// Snapshot payloads always describe the CURRENT row contents; tombstones keep payloadJson null
// (ExportSyncSnapshotPageUseCase decides), here we only fill updatedAt/deletedAt alongside.

private fun AccountEntity.toSnapshotSourceRow() = SyncSnapshotSourceRow(
    recordId = id,
    payloadJson = SyncMirrorPayloads.accountPayloadJson(toAccount()),
    updatedAt = listOfNotNull(createdAt, lastUsedAt, lastBalanceUpdateAt, closedAt).max(),
    deletedAt = null,
)

private fun CashFlowRecordEntity.toSnapshotSourceRow() = SyncSnapshotSourceRow(
    recordId = id,
    payloadJson = SyncMirrorPayloads.cashFlowPayloadJson(toDomainRecord()),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun TransferRecordEntity.toSnapshotSourceRow() = SyncSnapshotSourceRow(
    recordId = id,
    payloadJson = SyncMirrorPayloads.transferPayloadJson(toDomainRecord()),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun BalanceUpdateRecordEntity.toSnapshotSourceRow() = SyncSnapshotSourceRow(
    recordId = id,
    payloadJson = SyncMirrorPayloads.balanceUpdatePayloadJson(toDomainRecord()),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun BalanceAdjustmentRecordEntity.toSnapshotSourceRow() = SyncSnapshotSourceRow(
    recordId = id,
    payloadJson = SyncMirrorPayloads.balanceAdjustmentPayloadJson(toDomainRecord()),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun AccountEntity.toAccount() = Account(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    isHidden = isHidden,
    closedAt = closedAt,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
    iconName = iconName,
    kind = AccountKind.fromValue(kind),
)

private fun CashFlowRecordEntity.toDomainRecord() = CashFlowRecord(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun TransferRecordEntity.toDomainRecord() = TransferRecord(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BalanceUpdateRecordEntity.toDomainRecord() = BalanceUpdateRecord(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

private fun BalanceAdjustmentRecordEntity.toDomainRecord() = BalanceAdjustmentRecord(
    id = id,
    accountId = accountId,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)
