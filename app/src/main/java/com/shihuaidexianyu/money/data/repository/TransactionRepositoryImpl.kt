package com.shihuaidexianyu.money.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.HistoryRecordDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TransactionRepositoryImpl(
    private val database: RoomDatabase,
    private val cashFlowRecordDao: CashFlowRecordDao,
    private val transferRecordDao: TransferRecordDao,
    private val balanceUpdateRecordDao: BalanceUpdateRecordDao,
    private val balanceAdjustmentRecordDao: BalanceAdjustmentRecordDao,
    private val historyRecordDao: HistoryRecordDao,
) : TransactionRepository {
    override fun observeChangeVersion(): Flow<Long> {
        // Room invalidation flows publish only after a transaction commits and re-run even when an
        // UPDATE leaves the count unchanged. Avoid a separate eager counter that cannot roll back.
        return combine(
            cashFlowRecordDao.observeActiveCount(),
            transferRecordDao.observeActiveCount(),
            balanceUpdateRecordDao.observeCount(),
            balanceAdjustmentRecordDao.observeCount(),
        ) { cashFlowCount, transferCount, balanceUpdateCount, adjustmentCount ->
            cashFlowCount.toLong() +
                transferCount.toLong() +
                balanceUpdateCount.toLong() +
                adjustmentCount.toLong()
        }
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }

    override suspend fun insertCashFlowRecord(record: CashFlowRecord): LedgerInsertResult {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        val normalized = record.copy(note = record.note.trim())
        return database.withTransaction {
            cashFlowRecordDao.queryByOperationId(normalized.operationId)?.toDomain()?.let { existing ->
                return@withTransaction cashFlowReplayResult(existing, normalized)
            }
            val insertedId = cashFlowRecordDao.insertOrIgnore(normalized.toEntity())
            if (insertedId != INSERT_IGNORED) {
                return@withTransaction LedgerInsertResult(recordId = insertedId, inserted = true)
            }
            val existing = cashFlowRecordDao.queryByOperationId(normalized.operationId)?.toDomain()
                ?: throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, normalized.id)
            cashFlowReplayResult(existing, normalized)
        }
    }

    override suspend fun updateCashFlowRecord(record: CashFlowRecord, expectedUpdatedAt: Long): Boolean {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        return cashFlowRecordDao.updateActive(
            id = record.id,
            operationId = record.operationId,
            expectedUpdatedAt = expectedUpdatedAt,
            accountId = record.accountId,
            direction = record.direction,
            amount = record.amount,
            note = record.note.trim(),
            occurredAt = record.occurredAt,
            updatedAt = record.updatedAt,
        ).changed()
    }

    override suspend fun softDeleteCashFlowRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = cashFlowRecordDao.softDelete(id, operationId, expectedUpdatedAt, deletedAt).changed()

    override suspend fun restoreCashFlowRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = cashFlowRecordDao.restore(id, operationId, expectedDeletedAt, restoredAt).changed()

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? = cashFlowRecordDao.queryById(id)?.toDomain()

    override suspend fun queryStoredCashFlowRecordById(id: Long): CashFlowRecord? =
        cashFlowRecordDao.queryStoredById(id)?.toDomain()

    override suspend fun queryCashFlowRecordByOperationId(operationId: String): CashFlowRecord? =
        cashFlowRecordDao.queryByOperationId(operationId)?.toDomain()

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecord> = cashFlowRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord> = cashFlowRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord> = cashFlowRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun queryRecentCashFlowNotes(direction: String, accountId: Long?, limit: Int): List<String> {
        return cashFlowRecordDao.queryRecentNotes(direction = direction, accountId = accountId, limit = limit)
    }

    override suspend fun queryActiveCashFlowRecordsByDirectionBetween(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecord> {
        return cashFlowRecordDao.queryActiveByDirectionBetween(direction, startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun insertTransferRecord(record: TransferRecord): LedgerInsertResult {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        val normalized = record.copy(note = record.note.trim())
        return database.withTransaction {
            transferRecordDao.queryByOperationId(normalized.operationId)?.toDomain()?.let { existing ->
                return@withTransaction transferReplayResult(existing, normalized)
            }
            val insertedId = transferRecordDao.insertOrIgnore(normalized.toEntity())
            if (insertedId != INSERT_IGNORED) {
                return@withTransaction LedgerInsertResult(recordId = insertedId, inserted = true)
            }
            val existing = transferRecordDao.queryByOperationId(normalized.operationId)?.toDomain()
                ?: throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, normalized.id)
            transferReplayResult(existing, normalized)
        }
    }

    override suspend fun updateTransferRecord(record: TransferRecord, expectedUpdatedAt: Long): Boolean {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        return transferRecordDao.updateActive(
            id = record.id,
            operationId = record.operationId,
            expectedUpdatedAt = expectedUpdatedAt,
            fromAccountId = record.fromAccountId,
            toAccountId = record.toAccountId,
            amount = record.amount,
            note = record.note.trim(),
            occurredAt = record.occurredAt,
            updatedAt = record.updatedAt,
        ).changed()
    }

    override suspend fun softDeleteTransferRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = transferRecordDao.softDelete(id, operationId, expectedUpdatedAt, deletedAt).changed()

    override suspend fun restoreTransferRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = transferRecordDao.restore(id, operationId, expectedDeletedAt, restoredAt).changed()

    override suspend fun queryTransferRecordById(id: Long): TransferRecord? = transferRecordDao.queryById(id)?.toDomain()

    override suspend fun queryStoredTransferRecordById(id: Long): TransferRecord? =
        transferRecordDao.queryStoredById(id)?.toDomain()

    override suspend fun queryTransferRecordByOperationId(operationId: String): TransferRecord? =
        transferRecordDao.queryByOperationId(operationId)?.toDomain()

    override suspend fun queryAllTransferRecords(): List<TransferRecord> = transferRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecord> = transferRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryActiveTransferRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransferRecord> {
        return transferRecordDao.queryActiveBetween(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord> = transferRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String> {
        return transferRecordDao.queryRecentNotes(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            limit = limit,
        )
    }

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): LedgerInsertResult {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        return database.withTransaction {
            balanceUpdateRecordDao.queryByOperationId(record.operationId)?.toDomain()?.let { existing ->
                return@withTransaction balanceUpdateReplayResult(existing, record)
            }
            val insertedId = balanceUpdateRecordDao.insertOrIgnore(record.toEntity())
            if (insertedId != INSERT_IGNORED) {
                return@withTransaction LedgerInsertResult(recordId = insertedId, inserted = true)
            }
            val existing = balanceUpdateRecordDao.queryByOperationId(record.operationId)?.toDomain()
                ?: throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, record.id)
            balanceUpdateReplayResult(existing, record)
        }
    }

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord, expectedUpdatedAt: Long): Boolean {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        return balanceUpdateRecordDao.updateActive(
            id = record.id,
            operationId = record.operationId,
            expectedUpdatedAt = expectedUpdatedAt,
            accountId = record.accountId,
            actualBalance = record.actualBalance,
            systemBalanceBeforeUpdate = record.systemBalanceBeforeUpdate,
            delta = record.delta,
            occurredAt = record.occurredAt,
            updatedAt = record.updatedAt,
        ).changed()
    }

    override suspend fun softDeleteBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = balanceUpdateRecordDao.softDelete(id, operationId, expectedUpdatedAt, deletedAt).changed()

    override suspend fun restoreBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = balanceUpdateRecordDao.restore(id, operationId, expectedDeletedAt, restoredAt).changed()

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? = balanceUpdateRecordDao.queryById(id)?.toDomain()

    override suspend fun queryStoredBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? =
        balanceUpdateRecordDao.queryStoredById(id)?.toDomain()

    override suspend fun queryBalanceUpdateRecordByOperationId(operationId: String): BalanceUpdateRecord? =
        balanceUpdateRecordDao.queryByOperationId(operationId)?.toDomain()

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord> = balanceUpdateRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryBalanceUpdateRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<BalanceUpdateRecord> {
        return balanceUpdateRecordDao.queryBetween(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord> = balanceUpdateRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord? = balanceUpdateRecordDao.getLatestForAccount(accountId)?.toDomain()

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): LedgerInsertResult {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        return database.withTransaction {
            balanceAdjustmentRecordDao.queryByOperationId(record.operationId)?.toDomain()?.let { existing ->
                return@withTransaction balanceAdjustmentReplayResult(existing, record)
            }
            val insertedId = balanceAdjustmentRecordDao.insertOrIgnore(record.toEntity())
            if (insertedId != INSERT_IGNORED) {
                return@withTransaction LedgerInsertResult(recordId = insertedId, inserted = true)
            }
            val existing = balanceAdjustmentRecordDao.queryByOperationId(record.operationId)?.toDomain()
                ?: throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_ADJUSTMENT, record.id)
            balanceAdjustmentReplayResult(existing, record)
        }
    }

    override suspend fun updateBalanceAdjustmentRecord(
        record: BalanceAdjustmentRecord,
        expectedUpdatedAt: Long,
    ): Boolean {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        return balanceAdjustmentRecordDao.updateActive(
            id = record.id,
            operationId = record.operationId,
            expectedUpdatedAt = expectedUpdatedAt,
            accountId = record.accountId,
            delta = record.delta,
            occurredAt = record.occurredAt,
            updatedAt = record.updatedAt,
        ).changed()
    }

    override suspend fun softDeleteBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = balanceAdjustmentRecordDao.softDelete(id, operationId, expectedUpdatedAt, deletedAt).changed()

    override suspend fun restoreBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = balanceAdjustmentRecordDao.restore(id, operationId, expectedDeletedAt, restoredAt).changed()

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? = balanceAdjustmentRecordDao.queryById(id)?.toDomain()

    override suspend fun queryStoredBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? =
        balanceAdjustmentRecordDao.queryStoredById(id)?.toDomain()

    override suspend fun queryBalanceAdjustmentRecordByOperationId(operationId: String): BalanceAdjustmentRecord? =
        balanceAdjustmentRecordDao.queryByOperationId(operationId)?.toDomain()

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord> = balanceAdjustmentRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryBalanceAdjustmentRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<BalanceAdjustmentRecord> {
        return balanceAdjustmentRecordDao.queryBetween(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord> = balanceAdjustmentRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun sumInflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long =
        cashFlowRecordDao.sumInflowBetween(accountId, startInclusive, endExclusive)

    override suspend fun sumOutflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long =
        cashFlowRecordDao.sumOutflowBetween(accountId, startInclusive, endExclusive)

    override suspend fun sumTransferInBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long =
        transferRecordDao.sumTransferInBetween(accountId, startInclusive, endExclusive)

    override suspend fun sumTransferOutBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long =
        transferRecordDao.sumTransferOutBetween(accountId, startInclusive, endExclusive)

    override suspend fun sumAdjustmentBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long =
        balanceAdjustmentRecordDao.sumAdjustmentBetween(accountId, startInclusive, endExclusive)

    override suspend fun sumCashInflowBetween(startInclusive: Long, endExclusive: Long): Long =
        cashFlowRecordDao.sumCashInflowBetween(startInclusive, endExclusive)

    override suspend fun sumCashOutflowBetween(startInclusive: Long, endExclusive: Long): Long =
        cashFlowRecordDao.sumCashOutflowBetween(startInclusive, endExclusive)

    override suspend fun sumBalanceUpdateIncreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceUpdateRecordDao.sumPositiveDeltaBetween(startInclusive, endExclusive)

    override suspend fun sumBalanceUpdateDecreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceUpdateRecordDao.sumNegativeDeltaBetween(startInclusive, endExclusive)

    override suspend fun sumManualAdjustmentIncreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceAdjustmentRecordDao.sumPositiveManualAdjustmentBetween(startInclusive, endExclusive)

    override suspend fun sumManualAdjustmentDecreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceAdjustmentRecordDao.sumNegativeManualAdjustmentBetween(startInclusive, endExclusive)

    override suspend fun countActiveCashFlowRecordsBetween(startInclusive: Long, endExclusive: Long): Int =
        cashFlowRecordDao.countActiveBetween(startInclusive, endExclusive)

    override suspend fun countActiveTransferRecordsBetween(startInclusive: Long, endExclusive: Long): Int =
        transferRecordDao.countActiveBetween(startInclusive, endExclusive)

    override suspend fun countManualAdjustmentRecordsBetween(startInclusive: Long, endExclusive: Long): Int =
        balanceAdjustmentRecordDao.countBetween(startInclusive, endExclusive)

    override suspend fun queryActiveCashFlowRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecord> = cashFlowRecordDao.queryActiveBetween(startInclusive, endExclusive).map { it.toDomain() }

    override suspend fun queryPurposeTotals(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PurposeTotal> {
        return cashFlowRecordDao.queryPurposeTotals(direction, startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun queryDailyCashFlowTotals(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotal> {
        return cashFlowRecordDao.queryDailyTotals(startInclusive, endExclusive, zoneOffsetSeconds).map { it.toDomain() }
    }

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        return historyRecordDao.queryPage(
            keyword = escapeLikeLiteral(filters.keyword.trim().lowercase()),
            excludeKeyword = escapeLikeLiteral(filters.excludeKeyword.trim().lowercase()),
            accountId = filters.accountId,
            dateStartAt = filters.dateStartAt,
            dateEndAt = filters.dateEndAt,
            minAmount = filters.minAmount,
            maxAmount = filters.maxAmount,
            amountDirection = filters.amountDirection.name,
            cursorOccurredAt = cursor?.occurredAt,
            cursorSourceOrder = cursor?.sourceOrder ?: 0,
            cursorRecordId = cursor?.recordId ?: 0L,
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int {
        return historyRecordDao.count(
            keyword = escapeLikeLiteral(filters.keyword.trim().lowercase()),
            excludeKeyword = escapeLikeLiteral(filters.excludeKeyword.trim().lowercase()),
            accountId = filters.accountId,
            dateStartAt = filters.dateStartAt,
            dateEndAt = filters.dateEndAt,
            minAmount = filters.minAmount,
            maxAmount = filters.maxAmount,
            amountDirection = filters.amountDirection.name,
        )
    }

    private fun escapeLikeLiteral(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length + 4)
        for (ch in input) {
            when (ch) {
                '\\', '%', '_' -> builder.append('\\').append(ch)
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private fun Int.changed(): Boolean {
        return this == 1
    }

    private fun cashFlowReplayResult(existing: CashFlowRecord, requested: CashFlowRecord): LedgerInsertResult {
        rejectUnverifiableActiveEdit(
            kind = LedgerRecordKind.CASH_FLOW,
            operationId = requested.operationId,
            existingRecordId = existing.id,
            createdAt = existing.createdAt,
            updatedAt = existing.updatedAt,
            deletedAt = existing.deletedAt,
        )
        val samePayload = existing.accountId == requested.accountId &&
            existing.direction == requested.direction &&
            existing.amount == requested.amount &&
            existing.note.trim() == requested.note.trim() &&
            existing.occurredAt == requested.occurredAt
        return replayResult(
            samePayload = samePayload,
            kind = LedgerRecordKind.CASH_FLOW,
            operationId = requested.operationId,
            existingRecordId = existing.id,
        )
    }

    private fun transferReplayResult(existing: TransferRecord, requested: TransferRecord): LedgerInsertResult {
        rejectUnverifiableActiveEdit(
            kind = LedgerRecordKind.TRANSFER,
            operationId = requested.operationId,
            existingRecordId = existing.id,
            createdAt = existing.createdAt,
            updatedAt = existing.updatedAt,
            deletedAt = existing.deletedAt,
        )
        val samePayload = existing.fromAccountId == requested.fromAccountId &&
            existing.toAccountId == requested.toAccountId &&
            existing.amount == requested.amount &&
            existing.note.trim() == requested.note.trim() &&
            existing.occurredAt == requested.occurredAt
        return replayResult(
            samePayload = samePayload,
            kind = LedgerRecordKind.TRANSFER,
            operationId = requested.operationId,
            existingRecordId = existing.id,
        )
    }

    private fun balanceUpdateReplayResult(
        existing: BalanceUpdateRecord,
        requested: BalanceUpdateRecord,
    ): LedgerInsertResult {
        rejectUnverifiableActiveEdit(
            kind = LedgerRecordKind.BALANCE_UPDATE,
            operationId = requested.operationId,
            existingRecordId = existing.id,
            createdAt = existing.createdAt,
            updatedAt = existing.updatedAt,
            deletedAt = existing.deletedAt,
        )
        val samePayload = existing.accountId == requested.accountId &&
            existing.actualBalance == requested.actualBalance &&
            existing.occurredAt == requested.occurredAt
        return replayResult(
            samePayload = samePayload,
            kind = LedgerRecordKind.BALANCE_UPDATE,
            operationId = requested.operationId,
            existingRecordId = existing.id,
        )
    }

    private fun balanceAdjustmentReplayResult(
        existing: BalanceAdjustmentRecord,
        requested: BalanceAdjustmentRecord,
    ): LedgerInsertResult {
        rejectUnverifiableActiveEdit(
            kind = LedgerRecordKind.BALANCE_ADJUSTMENT,
            operationId = requested.operationId,
            existingRecordId = existing.id,
            createdAt = existing.createdAt,
            updatedAt = existing.updatedAt,
            deletedAt = existing.deletedAt,
        )
        val samePayload = existing.accountId == requested.accountId &&
            existing.delta == requested.delta &&
            existing.occurredAt == requested.occurredAt
        return replayResult(
            samePayload = samePayload,
            kind = LedgerRecordKind.BALANCE_ADJUSTMENT,
            operationId = requested.operationId,
            existingRecordId = existing.id,
        )
    }

    private fun replayResult(
        samePayload: Boolean,
        kind: LedgerRecordKind,
        operationId: String,
        existingRecordId: Long,
    ): LedgerInsertResult {
        if (!samePayload) {
            throw LedgerOperationConflictException(kind, operationId, existingRecordId)
        }
        return LedgerInsertResult(recordId = existingRecordId, inserted = false)
    }

    private fun rejectUnverifiableActiveEdit(
        kind: LedgerRecordKind,
        operationId: String,
        existingRecordId: Long,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
    ) {
        if (deletedAt == null && updatedAt != createdAt) {
            throw LedgerOperationConflictException(kind, operationId, existingRecordId)
        }
    }

    private fun requireOperationId(operationId: String) {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
    }

    private fun requireInsertableId(id: Long) {
        require(id == 0L) { "新建账本记录的 ID 必须为 0" }
    }

    private fun requireNewerTimestamp(updatedAt: Long, expectedUpdatedAt: Long) {
        require(updatedAt > expectedUpdatedAt) { "新的更新时间必须晚于原记录" }
    }

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
