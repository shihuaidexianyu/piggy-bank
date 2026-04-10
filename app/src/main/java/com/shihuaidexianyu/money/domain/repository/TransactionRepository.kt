package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeChangeVersion(): Flow<Long>
    suspend fun <T> runInTransaction(block: suspend () -> T): T
    suspend fun insertCashFlowRecord(record: CashFlowRecordEntity): Long
    suspend fun updateCashFlowRecord(record: CashFlowRecordEntity)
    suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long)
    suspend fun queryCashFlowRecordById(id: Long): CashFlowRecordEntity?
    suspend fun queryAllCashFlowRecords(): List<CashFlowRecordEntity>
    suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecordEntity>
    suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecordEntity>

    suspend fun insertTransferRecord(record: TransferRecordEntity): Long
    suspend fun updateTransferRecord(record: TransferRecordEntity)
    suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long)
    suspend fun queryTransferRecordById(id: Long): TransferRecordEntity?
    suspend fun queryAllTransferRecords(): List<TransferRecordEntity>
    suspend fun queryAllActiveTransferRecords(): List<TransferRecordEntity>
    suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecordEntity>
    suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecordEntity>

    suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecordEntity): Long
    suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecordEntity)
    suspend fun deleteBalanceUpdateRecord(id: Long)
    suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecordEntity?
    suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecordEntity>
    suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecordEntity>
    suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecordEntity>
    suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecordEntity?
    suspend fun getLatestBalanceUpdateAtOrBefore(accountId: Long, occurredAt: Long): BalanceUpdateRecordEntity?

    suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity): Long
    suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity)
    suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecordEntity?
    suspend fun deleteBalanceAdjustmentBySourceUpdateRecordId(sourceUpdateRecordId: Long)
    suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecordEntity>
    suspend fun queryManualBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecordEntity>
    suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity>

    suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long
    suspend fun sumAllInflowBetween(startAt: Long, endAt: Long): Long
    suspend fun sumAllOutflowBetween(startAt: Long, endAt: Long): Long
    suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecordEntity>
}
