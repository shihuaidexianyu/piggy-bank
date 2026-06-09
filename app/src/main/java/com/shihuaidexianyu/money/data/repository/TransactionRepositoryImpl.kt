package com.shihuaidexianyu.money.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class TransactionRepositoryImpl(
    private val database: RoomDatabase,
    private val cashFlowRecordDao: CashFlowRecordDao,
    private val transferRecordDao: TransferRecordDao,
    private val balanceUpdateRecordDao: BalanceUpdateRecordDao,
    private val balanceAdjustmentRecordDao: BalanceAdjustmentRecordDao,
) : TransactionRepository {
    private val mutationVersion = MutableStateFlow(0L)

    override fun observeChangeVersion(): Flow<Long> {
        return combine(
            cashFlowRecordDao.observeActiveCount(),
            transferRecordDao.observeActiveCount(),
            balanceUpdateRecordDao.observeCount(),
            balanceAdjustmentRecordDao.observeCount(),
            mutationVersion,
        ) { cashFlowCount, transferCount, balanceUpdateCount, adjustmentCount, version ->
            cashFlowCount.toLong() +
                transferCount.toLong() +
                balanceUpdateCount.toLong() +
                adjustmentCount.toLong() +
                version
        }
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }

    override suspend fun insertCashFlowRecord(record: CashFlowRecord): Long {
        return cashFlowRecordDao.insert(record.toEntity()).also { bumpVersion() }
    }

    override suspend fun updateCashFlowRecord(record: CashFlowRecord) {
        cashFlowRecordDao.update(record.toEntity())
        bumpVersion()
    }

    override suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long) {
        cashFlowRecordDao.softDelete(id, updatedAt)
        bumpVersion()
    }

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? = cashFlowRecordDao.queryById(id)?.toDomain()

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecord> = cashFlowRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord> = cashFlowRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord> = cashFlowRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun queryRecentCashFlowPurposes(direction: String, accountId: Long?, limit: Int): List<String> {
        return cashFlowRecordDao.queryRecentPurposes(direction = direction, accountId = accountId, limit = limit)
    }

    override suspend fun queryActiveCashFlowRecordsByDirectionBetween(
        direction: String,
        startAt: Long,
        endAt: Long,
    ): List<CashFlowRecord> {
        return cashFlowRecordDao.queryActiveByDirectionBetween(direction, startAt, endAt).map { it.toDomain() }
    }

    override suspend fun insertTransferRecord(record: TransferRecord): Long {
        return transferRecordDao.insert(record.toEntity()).also { bumpVersion() }
    }

    override suspend fun updateTransferRecord(record: TransferRecord) {
        transferRecordDao.update(record.toEntity())
        bumpVersion()
    }

    override suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long) {
        transferRecordDao.softDelete(id, updatedAt)
        bumpVersion()
    }

    override suspend fun queryTransferRecordById(id: Long): TransferRecord? = transferRecordDao.queryById(id)?.toDomain()

    override suspend fun queryAllTransferRecords(): List<TransferRecord> = transferRecordDao.queryAll().map { it.toDomain() }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecord> = transferRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecord> {
        return transferRecordDao.queryActiveBetween(startAt, endAt).map { it.toDomain() }
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord> = transferRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String> {
        return transferRecordDao.queryRecentNotes(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            limit = limit,
        )
    }

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): Long {
        return balanceUpdateRecordDao.insert(record.toEntity()).also { bumpVersion() }
    }

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord) {
        balanceUpdateRecordDao.update(record.toEntity())
        bumpVersion()
    }

    override suspend fun deleteBalanceUpdateRecord(id: Long) {
        balanceUpdateRecordDao.deleteById(id)
        bumpVersion()
    }

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? = balanceUpdateRecordDao.queryById(id)?.toDomain()

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord> = balanceUpdateRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecord> {
        return balanceUpdateRecordDao.queryBetween(startAt, endAt).map { it.toDomain() }
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord> = balanceUpdateRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord? = balanceUpdateRecordDao.getLatestForAccount(accountId)?.toDomain()

    override suspend fun getLatestBalanceUpdateAtOrBefore(accountId: Long, occurredAt: Long): BalanceUpdateRecord? {
        return balanceUpdateRecordDao.getLatestForAccountAtOrBefore(accountId, occurredAt)?.toDomain()
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): Long {
        return balanceAdjustmentRecordDao.insert(record.toEntity()).also { bumpVersion() }
    }

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord) {
        balanceAdjustmentRecordDao.update(record.toEntity())
        bumpVersion()
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? = balanceAdjustmentRecordDao.queryById(id)?.toDomain()

    override suspend fun deleteBalanceAdjustmentBySourceUpdateRecordId(sourceUpdateRecordId: Long) {
        balanceAdjustmentRecordDao.deleteBySourceUpdateRecordId(sourceUpdateRecordId)
        bumpVersion()
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord> = balanceAdjustmentRecordDao.queryAllActive().map { it.toDomain() }

    override suspend fun queryManualBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecord> {
        return balanceAdjustmentRecordDao.queryManualBetween(startAt, endAt).map { it.toDomain() }
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord> = balanceAdjustmentRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumInflowBetween(accountId, startAt, endAt)

    override suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long = cashFlowRecordDao.sumOutflowBetween(accountId, startAt, endAt)

    override suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long = transferRecordDao.sumTransferInBetween(accountId, startAt, endAt)

    override suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long = transferRecordDao.sumTransferOutBetween(accountId, startAt, endAt)

    override suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long = balanceAdjustmentRecordDao.sumAdjustmentBetween(accountId, startAt, endAt)

    override suspend fun sumCashInflowBetween(startAt: Long, endAt: Long): Long =
        cashFlowRecordDao.sumCashInflowBetween(startAt, endAt)

    override suspend fun sumCashOutflowBetween(startAt: Long, endAt: Long): Long =
        cashFlowRecordDao.sumCashOutflowBetween(startAt, endAt)

    override suspend fun sumBalanceUpdateIncreaseBetween(startAt: Long, endAt: Long): Long =
        balanceUpdateRecordDao.sumPositiveDeltaBetween(startAt, endAt)

    override suspend fun sumBalanceUpdateDecreaseBetween(startAt: Long, endAt: Long): Long =
        balanceUpdateRecordDao.sumNegativeDeltaBetween(startAt, endAt)

    override suspend fun sumManualAdjustmentIncreaseBetween(startAt: Long, endAt: Long): Long =
        balanceAdjustmentRecordDao.sumPositiveManualAdjustmentBetween(startAt, endAt)

    override suspend fun sumManualAdjustmentDecreaseBetween(startAt: Long, endAt: Long): Long =
        balanceAdjustmentRecordDao.sumNegativeManualAdjustmentBetween(startAt, endAt)

    override suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecord> = cashFlowRecordDao.queryActiveBetween(startAt, endAt).map { it.toDomain() }

    override suspend fun queryPurposeTotals(
        direction: String,
        startAt: Long,
        endAt: Long,
    ): List<PurposeTotal> {
        return cashFlowRecordDao.queryPurposeTotals(direction, startAt, endAt).map { it.toDomain() }
    }

    override suspend fun queryDailyCashFlowTotals(
        startAt: Long,
        endAt: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotal> {
        return cashFlowRecordDao.queryDailyTotals(startAt, endAt, zoneOffsetSeconds).map { it.toDomain() }
    }

    private fun bumpVersion() {
        mutationVersion.value = mutationVersion.value + 1
    }
}
