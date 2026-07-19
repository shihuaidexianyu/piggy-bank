package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HomePeriodLedgerSummary
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

/**
 * Composite repository for all transaction-ledger tables.
 *
 * Previously split into several feature-specific sub-interfaces. The split was removed because every
 * consumer was injected with the full composite anyway, so the sub-interfaces were dead documentation.
 * If you want to narrow a use case's dependency, prefer
 * extracting a focused port interface in `domain/repository/` and implementing it against the same
 * DAO layer, rather than reviving the sub-interface hierarchy.
 */
interface TransactionRepository : DatabaseTransactionRunner {
    // === Change observation / transactions ===
    fun observeChangeVersion(): Flow<Long>
    override suspend fun <T> runInTransaction(block: suspend () -> T): T

    // === Cash flow records ===
    suspend fun insertCashFlowRecord(record: CashFlowRecord): LedgerInsertResult
    suspend fun updateCashFlowRecord(record: CashFlowRecord, expectedUpdatedAt: Long): Boolean
    suspend fun softDeleteCashFlowRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean
    suspend fun restoreCashFlowRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean
    suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord?
    suspend fun queryStoredCashFlowRecordById(id: Long): CashFlowRecord?
    suspend fun queryCashFlowRecordByOperationId(operationId: String): CashFlowRecord?
    suspend fun queryAllCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord>
    suspend fun queryRecentCashFlowNotes(direction: String, accountId: Long?, limit: Int): List<String>
    // === Transfer records ===
    suspend fun insertTransferRecord(record: TransferRecord): LedgerInsertResult
    suspend fun updateTransferRecord(record: TransferRecord, expectedUpdatedAt: Long): Boolean
    suspend fun softDeleteTransferRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean
    suspend fun restoreTransferRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean
    suspend fun queryTransferRecordById(id: Long): TransferRecord?
    suspend fun queryStoredTransferRecordById(id: Long): TransferRecord?
    suspend fun queryTransferRecordByOperationId(operationId: String): TransferRecord?
    suspend fun queryAllTransferRecords(): List<TransferRecord>
    suspend fun queryAllActiveTransferRecords(): List<TransferRecord>
    suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord>
    suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String>

    // === Balance update records ===
    suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): LedgerInsertResult
    suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord, expectedUpdatedAt: Long): Boolean
    suspend fun softDeleteBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean
    suspend fun restoreBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean
    suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord?
    suspend fun queryStoredBalanceUpdateRecordById(id: Long): BalanceUpdateRecord?
    suspend fun queryBalanceUpdateRecordByOperationId(operationId: String): BalanceUpdateRecord?
    suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord>
    suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord>
    suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord?

    // === Balance adjustment records ===
    suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): LedgerInsertResult
    suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord, expectedUpdatedAt: Long): Boolean
    suspend fun softDeleteBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean
    suspend fun restoreBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean
    suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord?
    suspend fun queryStoredBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord?
    suspend fun queryBalanceAdjustmentRecordByOperationId(operationId: String): BalanceAdjustmentRecord?
    suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord>
    suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord>

    // === Period aggregates ===
    suspend fun sumInflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long
    suspend fun sumOutflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long
    suspend fun sumTransferInBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long
    suspend fun sumTransferOutBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long
    suspend fun sumAdjustmentBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long
    suspend fun queryHomePeriodLedgerSummary(
        startInclusive: Long,
        endExclusive: Long,
    ): HomePeriodLedgerSummary

    // === Unified history ===
    suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord>

    suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int
}
