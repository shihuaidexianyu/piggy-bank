package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

class InMemoryTransactionRepository : TransactionRepository {
    private var nextCashFlowId = 1L
    private var nextTransferId = 1L
    private var nextBalanceUpdateId = 1L
    private var nextAdjustmentId = 1L

    private val cashFlowRecords = mutableListOf<CashFlowRecord>()
    private val transferRecords = mutableListOf<TransferRecord>()
    private val balanceUpdates = mutableListOf<BalanceUpdateRecord>()
    private val adjustments = mutableListOf<BalanceAdjustmentRecord>()
    private val changeVersion = MutableStateFlow(0L)

    override fun observeChangeVersion(): Flow<Long> = changeVersion.asStateFlow()

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()

    override suspend fun insertCashFlowRecord(record: CashFlowRecord): Long {
        val id = nextCashFlowId++
        cashFlowRecords += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateCashFlowRecord(record: CashFlowRecord) {
        replaceCashFlowById(record.id, record)
        bumpVersion()
    }

    override suspend fun softDeleteCashFlowRecord(id: Long, updatedAt: Long) {
        val existing = queryCashFlowRecordById(id) ?: return
        updateCashFlowRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? {
        return cashFlowRecords.firstOrNull { it.id == id && !it.isDeleted }
    }

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecord> {
        return cashFlowRecords.toList()
    }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord> {
        return cashFlowRecords.filterNot(CashFlowRecord::isDeleted)
    }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords().filter { it.accountId == accountId }
    }

    override suspend fun queryRecentCashFlowPurposes(direction: String, accountId: Long?, limit: Int): List<String> {
        return queryAllActiveCashFlowRecords()
            .asSequence()
            .filter { it.direction == direction }
            .filter { accountId == null || it.accountId == accountId }
            .filter { it.purpose.isNotBlank() }
            .sortedWith(compareByDescending<CashFlowRecord> { it.occurredAt }.thenByDescending { it.id })
            .map { it.purpose }
            .distinct()
            .take(limit)
            .toList()
    }

    override suspend fun queryActiveCashFlowRecordsByDirectionBetween(
        direction: String,
        startAt: Long,
        endAt: Long,
    ): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == direction && it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<CashFlowRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertTransferRecord(record: TransferRecord): Long {
        val id = nextTransferId++
        transferRecords += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateTransferRecord(record: TransferRecord) {
        replaceTransferById(record.id, record)
        bumpVersion()
    }

    override suspend fun softDeleteTransferRecord(id: Long, updatedAt: Long) {
        val existing = queryTransferRecordById(id) ?: return
        updateTransferRecord(existing.copy(isDeleted = true, updatedAt = updatedAt))
    }

    override suspend fun queryTransferRecordById(id: Long): TransferRecord? {
        return transferRecords.firstOrNull { it.id == id && !it.isDeleted }
    }

    override suspend fun queryAllTransferRecords(): List<TransferRecord> {
        return transferRecords.toList()
    }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecord> {
        return transferRecords.filterNot(TransferRecord::isDeleted)
    }

    override suspend fun queryActiveTransferRecordsBetween(startAt: Long, endAt: Long): List<TransferRecord> {
        return queryAllActiveTransferRecords()
            .filter { it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<TransferRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryTransferRecordsByAccountId(accountId: Long): List<TransferRecord> {
        return queryAllActiveTransferRecords().filter {
            it.fromAccountId == accountId || it.toAccountId == accountId
        }
    }

    override suspend fun queryRecentTransferNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String> {
        return queryAllActiveTransferRecords()
            .asSequence()
            .filter { fromAccountId == null || it.fromAccountId == fromAccountId }
            .filter { toAccountId == null || it.toAccountId == toAccountId }
            .filter { it.note.isNotBlank() }
            .sortedWith(compareByDescending<TransferRecord> { it.occurredAt }.thenByDescending { it.id })
            .map { it.note }
            .distinct()
            .take(limit)
            .toList()
    }

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): Long {
        val id = nextBalanceUpdateId++
        balanceUpdates += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateBalanceUpdateRecord(record: BalanceUpdateRecord) {
        replaceBalanceUpdateById(record.id, record)
        bumpVersion()
    }

    override suspend fun deleteBalanceUpdateRecord(id: Long) {
        if (balanceUpdates.removeAll { it.id == id }) {
            bumpVersion()
        }
    }

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? {
        return balanceUpdates.firstOrNull { it.id == id }
    }

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord> {
        return balanceUpdates.toList()
    }

    override suspend fun queryBalanceUpdateRecordsBetween(startAt: Long, endAt: Long): List<BalanceUpdateRecord> {
        return balanceUpdates
            .filter { it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord> {
        return balanceUpdates.filter { it.accountId == accountId }
    }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .maxWithOrNull(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): Long {
        val id = nextAdjustmentId++
        adjustments += record.copy(id = id)
        bumpVersion()
        return id
    }

    override suspend fun updateBalanceAdjustmentRecord(record: BalanceAdjustmentRecord) {
        replaceAdjustmentById(record.id, record)
        bumpVersion()
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? {
        return adjustments.firstOrNull { it.id == id }
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord> {
        return adjustments.toList()
    }

    override suspend fun queryBalanceAdjustmentRecordsBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecord> {
        return adjustments
            .filter { it.occurredAt in startAt..endAt }
            .sortedWith(compareBy<BalanceAdjustmentRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord> {
        return adjustments.filter { it.accountId == accountId }
    }

    override suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == CashFlowDirection.INFLOW.value && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter { it.direction == CashFlowDirection.OUTFLOW.value && it.occurredAt > startAt && it.occurredAt <= endAt }
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

    override suspend fun sumCashInflowBetween(startAt: Long, endAt: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == CashFlowDirection.INFLOW.value && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumCashOutflowBetween(startAt: Long, endAt: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == CashFlowDirection.OUTFLOW.value && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.amount }
    }

    override suspend fun sumBalanceUpdateIncreaseBetween(startAt: Long, endAt: Long): Long =
        balanceUpdates
            .filter { it.delta > 0 && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.delta }

    override suspend fun sumBalanceUpdateDecreaseBetween(startAt: Long, endAt: Long): Long =
        balanceUpdates
            .filter { it.delta < 0 && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { -it.delta }

    override suspend fun sumManualAdjustmentIncreaseBetween(startAt: Long, endAt: Long): Long =
        adjustments
            .filter { it.delta > 0 && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { it.delta }

    override suspend fun sumManualAdjustmentDecreaseBetween(startAt: Long, endAt: Long): Long =
        adjustments
            .filter { it.delta < 0 && it.occurredAt > startAt && it.occurredAt <= endAt }
            .sumOf { -it.delta }

    override suspend fun countActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): Int {
        return queryAllActiveCashFlowRecords()
            .count { it.occurredAt > startAt && it.occurredAt <= endAt }
    }

    override suspend fun countActiveTransferRecordsBetween(startAt: Long, endAt: Long): Int {
        return queryAllActiveTransferRecords()
            .count { it.occurredAt > startAt && it.occurredAt <= endAt }
    }

    override suspend fun countManualAdjustmentRecordsBetween(startAt: Long, endAt: Long): Int {
        return adjustments.count { it.occurredAt > startAt && it.occurredAt <= endAt }
    }

    override suspend fun queryActiveCashFlowRecordsBetween(startAt: Long, endAt: Long): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords()
            .filter { it.occurredAt in startAt..endAt }
            .sortedBy { it.occurredAt }
    }

    override suspend fun queryPurposeTotals(direction: String, startAt: Long, endAt: Long): List<PurposeTotal> {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == direction && it.occurredAt in startAt..endAt }
            .groupBy { it.purpose.ifBlank { "未填写用途" } }
            .map { (purpose, records) -> PurposeTotal(purpose = purpose, amount = records.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
    }

    override suspend fun queryDailyCashFlowTotals(
        startAt: Long,
        endAt: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotal> {
        val offset = ZoneOffset.ofTotalSeconds(zoneOffsetSeconds)
        return queryAllActiveCashFlowRecords()
            .filter { it.occurredAt in startAt..endAt }
            .groupBy { record ->
                val epochDay = Instant.ofEpochMilli(record.occurredAt).atOffset(offset).toLocalDate().toEpochDay()
                epochDay to record.direction
            }
            .map { (key, records) ->
                CashFlowDailyTotal(
                    epochDay = key.first,
                    direction = key.second,
                    amount = records.sumOf { it.amount },
                )
            }
            .sortedWith(compareBy<CashFlowDailyTotal> { it.epochDay }.thenBy { it.direction })
    }

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        return buildHistoryRecords()
            .asSequence()
            .filter { it.matches(filters) }
            .filter { it.isAfterCursor(cursor) }
            .take(limit)
            .toList()
    }

    override suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int {
        return buildHistoryRecords().count { it.matches(filters) }
    }

    private fun buildHistoryRecords(): List<HistoryRecord> {
        val cashRecords = cashFlowRecords.filterNot(CashFlowRecord::isDeleted).map { record ->
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.CASH_FLOW,
                sourceOrder = 4,
                accountId = record.accountId,
                relatedAccountId = null,
                title = record.purpose.ifBlank { "未填写用途" },
                amount = if (record.direction == CashFlowDirection.INFLOW.value) record.amount else -record.amount,
                occurredAt = record.occurredAt,
                keywordSource = record.purpose,
            )
        }
        val transferHistoryRecords = transferRecords.filterNot(TransferRecord::isDeleted).map { record ->
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.TRANSFER,
                sourceOrder = 3,
                accountId = record.fromAccountId,
                relatedAccountId = record.toAccountId,
                title = record.note.ifBlank { "账户间转移" },
                amount = record.amount,
                occurredAt = record.occurredAt,
                keywordSource = record.note,
            )
        }
        val updateHistoryRecords = balanceUpdates.map { record ->
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.BALANCE_UPDATE,
                sourceOrder = 2,
                accountId = record.accountId,
                relatedAccountId = null,
                title = if (record.delta == 0L) "余额核对" else "对账调整",
                amount = record.delta,
                occurredAt = record.occurredAt,
                keywordSource = "",
            )
        }
        val adjustmentHistoryRecords = adjustments.map { record ->
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.BALANCE_ADJUSTMENT,
                sourceOrder = 1,
                accountId = record.accountId,
                relatedAccountId = null,
                title = "余额矫正",
                amount = record.delta,
                occurredAt = record.occurredAt,
                keywordSource = "",
            )
        }
        return (cashRecords + transferHistoryRecords + updateHistoryRecords + adjustmentHistoryRecords)
            .sortedWith(
                compareByDescending<HistoryRecord> { it.occurredAt }
                    .thenByDescending { it.sourceOrder }
                    .thenByDescending { it.recordId },
            )
    }

    private fun HistoryRecord.matches(filters: HistoryRecordFilters): Boolean {
        val keyword = filters.keyword.trim().lowercase()
        val excludeKeyword = filters.excludeKeyword.trim().lowercase()
        val source = keywordSource.lowercase()
        val keywordOk = keyword.isBlank() || source.contains(keyword)
        val excludeOk = excludeKeyword.isBlank() || !source.contains(excludeKeyword)
        val accountOk = filters.accountId == null ||
            accountId == filters.accountId ||
            relatedAccountId == filters.accountId
        val startOk = filters.dateStartAt == null || occurredAt >= filters.dateStartAt
        val endOk = filters.dateEndAt == null || occurredAt <= filters.dateEndAt
        val amountAbs = abs(amount)
        val minOk = filters.minAmount == null || amountAbs >= filters.minAmount
        val maxOk = filters.maxAmount == null || amountAbs <= filters.maxAmount
        val directionOk = when (filters.amountDirection) {
            HistoryAmountDirection.ALL -> true
            HistoryAmountDirection.INCREASE -> amount > 0 && type != HistoryRecordType.TRANSFER
            HistoryAmountDirection.DECREASE -> amount < 0 && type != HistoryRecordType.TRANSFER
        }
        return keywordOk && excludeOk && accountOk && startOk && endOk && minOk && maxOk && directionOk
    }

    private fun HistoryRecord.isAfterCursor(cursor: HistoryPageCursor?): Boolean {
        return cursor == null ||
            occurredAt < cursor.occurredAt ||
            (occurredAt == cursor.occurredAt && sourceOrder < cursor.sourceOrder) ||
            (occurredAt == cursor.occurredAt && sourceOrder == cursor.sourceOrder && recordId < cursor.recordId)
    }

    private fun replaceCashFlowById(id: Long, replacement: CashFlowRecord) {
        val index = cashFlowRecords.indexOfFirst { it.id == id }
        if (index >= 0) cashFlowRecords[index] = replacement
    }

    private fun replaceTransferById(id: Long, replacement: TransferRecord) {
        val index = transferRecords.indexOfFirst { it.id == id }
        if (index >= 0) transferRecords[index] = replacement
    }

    private fun replaceBalanceUpdateById(id: Long, replacement: BalanceUpdateRecord) {
        val index = balanceUpdates.indexOfFirst { it.id == id }
        if (index >= 0) balanceUpdates[index] = replacement
    }

    private fun replaceAdjustmentById(id: Long, replacement: BalanceAdjustmentRecord) {
        val index = adjustments.indexOfFirst { it.id == id }
        if (index >= 0) adjustments[index] = replacement
    }

    private fun bumpVersion() {
        changeVersion.value = changeVersion.value + 1
    }
}
