package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowTemplate
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

interface TransactionChangeRepository {
    fun observeChangeVersion(): Flow<Long>
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

interface CashFlowTransactionRepository {
    suspend fun insertCashFlowRecord(record: CashFlowRecord): Long
    suspend fun updateCashFlowRecord(record: CashFlowRecord)
    suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long)
    suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord?
    suspend fun queryAllCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord>
    suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord>
    suspend fun queryRecentCashFlowPurposes(direction: String, accountId: Long?, limit: Int): List<String>
    suspend fun queryRecentCashFlowTemplates(direction: String, accountId: Long?, limit: Int): List<CashFlowTemplate>
    suspend fun queryActiveCashFlowRecordsByDirectionBetween(direction: String, startAt: Long, endAt: Long): List<CashFlowRecord>
    suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecord>
    suspend fun queryPurposeTotals(direction: String, startAt: Long, endAt: Long): List<PurposeTotal>
    suspend fun queryDailyCashFlowTotals(startAt: Long, endAt: Long, zoneOffsetSeconds: Int): List<CashFlowDailyTotal>
}

interface TransferTransactionRepository {
    suspend fun insertTransferRecord(record: TransferRecord): Long
    suspend fun updateTransferRecord(record: TransferRecord)
    suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long)
    suspend fun queryTransferRecordById(id: Long): TransferRecord?
    suspend fun queryAllTransferRecords(): List<TransferRecord>
    suspend fun queryAllActiveTransferRecords(): List<TransferRecord>
    suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecord>
    suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord>
    suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String>
}

interface BalanceUpdateTransactionRepository {
    suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): Long
    suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord)
    suspend fun deleteBalanceUpdateRecord(id: Long)
    suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord?
    suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord>
    suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecord>
    suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord>
    suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord?
    suspend fun getLatestBalanceUpdateAtOrBefore(accountId: Long, occurredAt: Long): BalanceUpdateRecord?
}

interface BalanceAdjustmentTransactionRepository {
    suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): Long
    suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord)
    suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord?
    suspend fun deleteBalanceAdjustmentBySourceUpdateRecordId(sourceUpdateRecordId: Long)
    suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord>
    suspend fun queryManualBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecord>
    suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord>
}

interface TransactionStatsRepository {
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
}

interface TransactionRepository :
    TransactionChangeRepository,
    CashFlowTransactionRepository,
    TransferTransactionRepository,
    BalanceUpdateTransactionRepository,
    BalanceAdjustmentTransactionRepository,
    TransactionStatsRepository
