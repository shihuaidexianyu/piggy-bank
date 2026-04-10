package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryTransactionRepository : TransactionRepository {
    private var nextCashFlowId = 1L
    private var nextTransferId = 1L
    private var nextBalanceUpdateId = 1L
    private var nextAdjustmentId = 1L

    private val cashFlowRecords = mutableListOf<CashFlowRecordEntity>()
    private val transferRecords = mutableListOf<TransferRecordEntity>()
    private val balanceUpdates = mutableListOf<BalanceUpdateRecordEntity>()
    private val adjustments = mutableListOf<BalanceAdjustmentRecordEntity>()
    private val changeVersion = MutableStateFlow(0L)

    override fun observeChangeVersion(): Flow<Long> = changeVersion.asStateFlow()

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()

    override suspend fun insertCashFlowRecord(record: CashFlowRecordEntity): Long {
        val id = nextCashFlowId++
        cashFlowRecords += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateCashFlowRecord(record: CashFlowRecordEntity) {
        replaceById(cashFlowRecords, record.id, record)
        bumpVersion()
    }

    override suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long) {
        val existing = queryCashFlowRecordById(id) ?: return
        updateCashFlowRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecordEntity? {
        return cashFlowRecords.firstOrNull { it.id == id && !it.isDeleted }
    }

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecordEntity> {
        return cashFlowRecords.toList()
    }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecordEntity> {
        return cashFlowRecords.filterNot(CashFlowRecordEntity::isDeleted)
    }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecordEntity> {
        return queryAllActiveCashFlowRecords().filter { it.accountId == accountId }
    }

    override suspend fun insertTransferRecord(record: TransferRecordEntity): Long {
        val id = nextTransferId++
        transferRecords += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateTransferRecord(record: TransferRecordEntity) {
        replaceById(transferRecords, record.id, record)
        bumpVersion()
    }

    override suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long) {
        val existing = queryTransferRecordById(id) ?: return
        updateTransferRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryTransferRecordById(id: Long): TransferRecordEntity? {
        return transferRecords.firstOrNull { it.id == id && !it.isDeleted }
    }

    override suspend fun queryAllTransferRecords(): List<TransferRecordEntity> {
        return transferRecords.toList()
    }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecordEntity> {
        return transferRecords.filterNot(TransferRecordEntity::isDeleted)
    }

    override suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecordEntity> {
        return queryAllActiveTransferRecords()
            .filter { it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<TransferRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecordEntity> {
        return queryAllActiveTransferRecords().filter {
            it.fromAccountId == accountId || it.toAccountId == accountId
        }
    }

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecordEntity): Long {
        val id = nextBalanceUpdateId++
        balanceUpdates += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecordEntity) {
        replaceById(balanceUpdates, record.id, record)
        bumpVersion()
    }

    override suspend fun deleteBalanceUpdateRecord(id: Long) {
        if (balanceUpdates.removeAll { it.id == id }) {
            bumpVersion()
        }
    }

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecordEntity? {
        return balanceUpdates.firstOrNull { it.id == id }
    }

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecordEntity> {
        return balanceUpdates.toList()
    }

    override suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecordEntity> {
        return balanceUpdates
            .filter { it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecordEntity> {
        return balanceUpdates.filter { it.accountId == accountId }
    }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecordEntity? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .maxWithOrNull(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun getLatestBalanceUpdateAtOrBefore(
        accountId: Long,
        occurredAt: Long,
    ): BalanceUpdateRecordEntity? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .filter { it.occurredAt <= occurredAt }
            .maxWithOrNull(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity): Long {
        val id = nextAdjustmentId++
        adjustments += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecordEntity) {
        replaceById(adjustments, record.id, record)
        bumpVersion()
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecordEntity? {
        return adjustments.firstOrNull { it.id == id }
    }

    override suspend fun deleteBalanceAdjustmentBySourceUpdateRecordId(sourceUpdateRecordId: Long) {
        if (adjustments.removeAll { it.sourceUpdateRecordId == sourceUpdateRecordId }) {
            bumpVersion()
        }
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecordEntity> {
        return adjustments.toList()
    }

    override suspend fun queryManualBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecordEntity> {
        return adjustments
            .filter { it.sourceUpdateRecordId == 0L && it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<BalanceAdjustmentRecordEntity> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity> {
        return adjustments.filter { it.accountId == accountId && it.sourceUpdateRecordId == 0L }
    }

    override suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == "inflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == "outflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.toAccountId == accountId && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.fromAccountId == accountId && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryBalanceAdjustmentRecordsByAccountId(accountId)
            .filter { it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.delta }
    }

    override suspend fun sumAllInflowBetween(startAt: Long, endAt: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == "inflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumAllOutflowBetween(startAt: Long, endAt: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == "outflow" && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecordEntity> {
        return queryAllActiveCashFlowRecords()
            .filter { it.occurredAt in startAt..endAt }
            .sortedBy { it.occurredAt }
    }

    private fun <T> replaceById(target: MutableList<T>, id: Long, replacement: T) {
        val index = target.indexOfFirst {
            when (it) {
                is CashFlowRecordEntity -> it.id == id
                is TransferRecordEntity -> it.id == id
                is BalanceUpdateRecordEntity -> it.id == id
                is BalanceAdjustmentRecordEntity -> it.id == id
                else -> false
            }
        }
        if (index >= 0) target[index] = replacement
    }

    private fun bumpVersion() {
        changeVersion.value = changeVersion.value + 1
    }
}
