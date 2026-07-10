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
    private val historyRecordDao: HistoryRecordDao,
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
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecord> {
        return cashFlowRecordDao.queryActiveByDirectionBetween(direction, startInclusive, endExclusive).map { it.toDomain() }
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

    override suspend fun queryBalanceUpdateRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<BalanceUpdateRecord> {
        return balanceUpdateRecordDao.queryBetween(startInclusive, endExclusive).map { it.toDomain() }
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord> = balanceUpdateRecordDao.queryByAccountId(accountId).map { it.toDomain() }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord? = balanceUpdateRecordDao.getLatestForAccount(accountId)?.toDomain()

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): Long {
        return balanceAdjustmentRecordDao.insert(record.toEntity()).also { bumpVersion() }
    }

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord) {
        balanceAdjustmentRecordDao.update(record.toEntity())
        bumpVersion()
    }

    override suspend fun deleteBalanceAdjustmentRecord(id: Long) {
        balanceAdjustmentRecordDao.deleteById(id)
        bumpVersion()
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? = balanceAdjustmentRecordDao.queryById(id)?.toDomain()

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord> = balanceAdjustmentRecordDao.queryAllActive().map { it.toDomain() }

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

    private fun bumpVersion() {
        mutationVersion.value = mutationVersion.value + 1
    }
}
