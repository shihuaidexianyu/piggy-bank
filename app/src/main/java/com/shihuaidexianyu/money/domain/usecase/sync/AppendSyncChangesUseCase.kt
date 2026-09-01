package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorPayloads
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

/**
 * Appends sync change-log entries for ledger mutations. Callers MUST invoke these methods inside
 * the same Room transaction as the mutation itself, so ledger and change-log commit atomically.
 *
 * Accounts have no `updatedAt` version token and are never deleted: account entries are always
 * upserts whose `updatedAt` is the change registration time, never a concurrency token.
 */
class AppendSyncChangesUseCase(
    private val syncRepository: SyncRepository,
    private val clockProvider: ClockProvider,
) {
    suspend fun upsertAccount(account: Account, requestId: String? = null): Long =
        append(
            NewSyncChange(
                entityKind = SyncEntityKind.ACCOUNT,
                recordId = account.id,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = SyncMirrorPayloads.accountPayloadJson(account),
                updatedAt = clockProvider.nowMillis(),
                requestId = requestId,
            ),
        )

    suspend fun upsertCashFlow(record: CashFlowRecord, requestId: String? = null): Long =
        append(
            NewSyncChange(
                entityKind = SyncEntityKind.CASH_FLOW,
                recordId = record.id,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = SyncMirrorPayloads.cashFlowPayloadJson(record),
                updatedAt = record.updatedAt,
                requestId = requestId,
            ),
        )

    suspend fun upsertTransfer(record: TransferRecord, requestId: String? = null): Long =
        append(
            NewSyncChange(
                entityKind = SyncEntityKind.TRANSFER,
                recordId = record.id,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = SyncMirrorPayloads.transferPayloadJson(record),
                updatedAt = record.updatedAt,
                requestId = requestId,
            ),
        )

    suspend fun upsertBalanceUpdate(record: BalanceUpdateRecord, requestId: String? = null): Long =
        append(
            NewSyncChange(
                entityKind = SyncEntityKind.BALANCE_UPDATE,
                recordId = record.id,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = SyncMirrorPayloads.balanceUpdatePayloadJson(record),
                updatedAt = record.updatedAt,
                requestId = requestId,
            ),
        )

    suspend fun upsertBalanceAdjustment(record: BalanceAdjustmentRecord, requestId: String? = null): Long =
        append(
            NewSyncChange(
                entityKind = SyncEntityKind.BALANCE_ADJUSTMENT,
                recordId = record.id,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = SyncMirrorPayloads.balanceAdjustmentPayloadJson(record),
                updatedAt = record.updatedAt,
                requestId = requestId,
            ),
        )

    /** Delete tombstone: no payload; [deletedAt] equals the record's post-delete updatedAt. */
    suspend fun deleteRecord(
        entityKind: SyncEntityKind,
        recordId: Long,
        updatedAt: Long,
        deletedAt: Long,
        requestId: String? = null,
    ): Long = append(
        NewSyncChange(
            entityKind = entityKind,
            recordId = recordId,
            operation = SyncChangeOperation.DELETE,
            payloadJson = null,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            requestId = requestId,
        ),
    )

    private suspend fun append(change: NewSyncChange): Long = syncRepository.appendChange(change)
}
