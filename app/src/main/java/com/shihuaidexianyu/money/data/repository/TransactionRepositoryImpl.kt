package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TransactionRepositoryImpl(
    private val database: RoomDatabase,
    private val cashFlowRecordDao: CashFlowRecordDao,
    private val transferRecordDao: TransferRecordDao,
    private val balanceUpdateRecordDao: BalanceUpdateRecordDao,
    private val balanceAdjustmentRecordDao: BalanceAdjustmentRecordDao,
) : TransactionRepository {
    override fun observeChangeVersion(): Flow<Long> {
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

    override suspend fun insertCashFlowRecord(record: CashFlowRecordEntity): Long = cashFlowRecordDao.insert(record)

    override suspend fun updateCashFlowRecord(record: CashFlowRecordEntity) = cashFlowRecordDao.update(record)

    override suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long) = cashFlowRecordDao.softDelete(id, updatedAt)

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecordEntity? = cashFlowRecordDao.queryById(id)

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecordEntity> = cashFlowRecordDao.queryAll()

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecordEntity> = cashFlowRecordDao.queryAllActive()

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecordEntity> = cashFlowRecordDao.queryByAccountId(accountId)

    override suspend fun insertTransferRecord(record: TransferRecordEntity): Long = transferRecordDao.insert(record)

    override suspend fun updateTransferRecord(record: TransferRecordEntity) = transferRecordDao.update(record)

    override suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long) = transferRecordDao.softDelete(id, updatedAt)

    override suspend fun queryTransferRecordById(id: Long): TransferRecordEntity? = transferRecordDao.queryById(id)

    override suspend fun queryAllTransferRecords(): List<TransferRecordEntity> = transferRecordDao.queryAll()

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecordEntity> = transferRecordDao.queryAllActive()

    override suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecordEntity> {
        return transferRecordDao.queryActiveBetween(startAt, endAt)
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecordEntity> = transferRecordDao.queryByAccountId(accountId)

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecordEntity): Long = balanceUpdateRecordDao.insert(record)

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecordEntity) = balanceUpdateRecordDao.update(record)

    override suspend fun deleteBalanceUpdateRecord(id: Long) = balanceUpdateRecordDao.deleteById(id)

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecordEntity? = balanceUpdateRecordDao.queryById(id)

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecordEntity> = balanceUpdateRecordDao.queryAllActive()

    override suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecordEntity> {
        return balanceUpdateRecordDao.queryBetween(startAt, endAt)
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecordEntity> = balanceUpdateRecordDao.queryByAccountId(accountId)

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecordEntity? = balanceUpdateRecordDao.getLatestForAccount(accountId)

    override suspend fun getLatestBalanceUpdateAtOrBefore(accountId: Long, occurredAt: Long): BalanceUpdateRecordEntity? {
        return balanceUpdateRecordDao.getLatestForAccountAtOrBefore(accountId, occurredAt)
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity): Long = balanceAdjustmentRecordDao.insert(record)

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity) = balanceAdjustmentRecordDao.update(record)

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecordEntity? = balanceAdjustmentRecordDao.queryById(id)

    override suspend fun deleteBalanceAdjustmentBySourceUpdateRecordId(sourceUpdateRecordId: Long) {
        balanceAdjustmentRecordDao.deleteBySourceUpdateRecordId(sourceUpdateRecordId)
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecordEntity> = balanceAdjustmentRecordDao.queryAllActive()

    override suspend fun queryManualBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecordEntity> {
        return balanceAdjustmentRecordDao.queryManualBetween(startAt, endAt)
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity> = balanceAdjustmentRecordDao.queryByAccountId(accountId)

    override suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumInflowBetween(accountId, startAt, endAt)

    override suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumOutflowBetween(accountId, startAt, endAt)

    override suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long = transferRecordDao.sumTransferInBetween(accountId, startAt, endAt)

    override suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long = transferRecordDao.sumTransferOutBetween(accountId, startAt, endAt)

    override suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long = balanceAdjustmentRecordDao.sumAdjustmentBetween(accountId, startAt, endAt)

    override suspend fun sumAllInflowBetween(startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumAllInflowBetween(startAt, endAt)

    override suspend fun sumAllOutflowBetween(startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumAllOutflowBetween(startAt, endAt)

    override suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecordEntity> = cashFlowRecordDao.queryActiveBetween(startAt, endAt)
}
