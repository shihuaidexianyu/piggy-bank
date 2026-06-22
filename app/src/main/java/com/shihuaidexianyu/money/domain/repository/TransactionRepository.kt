package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

/**
 * Composite repository for all transaction-ledger tables.
 *
 * Previously split into 7 sub-interfaces (CashFlow/Transfer/BalanceUpdate/BalanceAdjustment/Stats/History/Change).
 * The split was removed because every consumer was injected with the full composite anyway, so the
 * sub-interfaces were dead documentation. If you want to narrow a use case's dependency, prefer
 * extracting a focused port interface in `domain/repository/` and implementing it against the same
 * DAO layer, rather than reviving the sub-interface hierarchy.
 */
interface TransactionRepository {
    // === Change observation / transactions ===
    fun observeChangeVersion(): Flow<Long>
    suspend fun <T> runInTransaction(block: suspend () -> T): T

    // === Cash flow records ===
    suspend fun insertCashFlowRecord(record: CashFlowRecord): Long
    suspend fun updateCashFlowRecord(record: CashFlowRecord)
    suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long)
    suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord?
    suspend fun queryAllCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord>
    suspend fun queryRecentCashFlowPurposes(direction: String, accountId: Long?, limit: Int): List<String>
    suspend fun queryActiveCashFlowRecordsByDirectionBetween(direction: String, startAt: Long, endAt: Long): List<CashFlowRecord>
    suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecord>
    suspend fun queryPurposeTotals(direction: String, startAt: Long, endAt: Long): List<PurposeTotal>
    suspend fun queryDailyCashFlowTotals(startAt: Long, endAt: Long, zoneOffsetSeconds: Int): List<CashFlowDailyTotal>

    // === Transfer records ===
    suspend fun insertTransferRecord(record: TransferRecord): Long
    suspend fun updateTransferRecord(record: TransferRecord)
    suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long)
    suspend fun queryTransferRecordById(id: Long): TransferRecord?
    suspend fun queryAllTransferRecords(): List<TransferRecord>
    suspend fun queryAllActiveTransferRecords(): List<TransferRecord>
    suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecord>
    suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord>
    suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String>

    // === Balance update records ===
    suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): Long
    suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord)
    suspend fun deleteBalanceUpdateRecord(id: Long)
    suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord?
    suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord>
    suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecord>
    suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord>
    suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord?

    // === Balance adjustment records ===
    suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): Long
    suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord)
    suspend fun deleteBalanceAdjustmentRecord(id: Long)
    suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord?
    suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord>
    suspend fun queryBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecord>
    suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord>

    // === Aggregated stats ===
    suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumCashInflowBetween(startAt: Long, endAt: Long): Long
    suspend fun sumCashOutflowBetween(startAt: Long, endAt: Long): Long
    suspend fun sumBalanceUpdateIncreaseBetween(startAt: Long, endAt: Long): Long
    suspend fun sumBalanceUpdateDecreaseBetween(startAt: Long, endAt: Long): Long
    suspend fun sumManualAdjustmentIncreaseBetween(startAt: Long, endAt: Long): Long
    suspend fun sumManualAdjustmentDecreaseBetween(startAt: Long, endAt: Long): Long
    suspend fun countActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): Int
    suspend fun countActiveTransferRecordsBetween(startAt: Long, endAt: Long): Int
    suspend fun countManualAdjustmentRecordsBetween(startAt: Long, endAt: Long): Int

    // === Unified history ===
    suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord>

    suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int
}
