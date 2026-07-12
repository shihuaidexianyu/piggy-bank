package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountActivityMaxima
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowAnalysisEntry
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.TransferPathTotal
import com.shihuaidexianyu.money.domain.model.normalizeHistorySearchText
import com.shihuaidexianyu.money.domain.model.requireValidAmountBounds
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.HomePeriodLedgerSummary
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.TimeMath
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset

class InMemoryTransactionRepository(
    private val accountNameLookup: (Long) -> String? = { null },
) : TransactionRepository, LedgerAggregateRepository {
    var transactionInvocationCount: Int = 0
        private set
    var homePeriodSummaryInvocationCount: Int = 0
        private set

    private var nextCashFlowId = 1L
    private var nextTransferId = 1L
    private var nextBalanceUpdateId = 1L
    private var nextAdjustmentId = 1L

    private val cashFlowRecords = mutableListOf<CashFlowRecord>()
    private val transferRecords = mutableListOf<TransferRecord>()
    private val balanceUpdates = mutableListOf<BalanceUpdateRecord>()
    private val adjustments = mutableListOf<BalanceAdjustmentRecord>()
    private val changeVersion = MutableStateFlow(0L)
    private val ledgerLock = Any()
    private val transactionMutex = Mutex()

    override fun observeChangeVersion(): Flow<Long> = changeVersion.asStateFlow()

    override suspend fun queryBefore(
        accounts: List<Account>,
        endExclusive: Long,
        excludingBalanceUpdateId: Long?,
    ): Map<Long, AccountLedgerAggregate> = aggregate(
        accounts = accounts,
        boundary = endExclusive,
        inclusiveEnd = false,
        excludingBalanceUpdateId = excludingBalanceUpdateId,
    )

    override suspend fun queryAt(
        accounts: List<Account>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long?,
    ): Map<Long, AccountLedgerAggregate> = aggregate(
        accounts = accounts,
        boundary = atTimeMillis,
        inclusiveEnd = true,
        excludingBalanceUpdateId = excludingBalanceUpdateId,
    )

    override suspend fun queryActivityMaxima(accountId: Long): AccountActivityMaxima = synchronized(ledgerLock) {
        val cashMax = cashFlowRecords.asSequence()
            .filter { it.deletedAt == null && it.accountId == accountId }
            .maxOfOrNull { it.occurredAt }
        val transferMax = transferRecords.asSequence()
            .filter { it.deletedAt == null && (it.fromAccountId == accountId || it.toAccountId == accountId) }
            .maxOfOrNull { it.occurredAt }
        val balanceUpdateMax = balanceUpdates.asSequence()
            .filter { it.deletedAt == null && it.accountId == accountId }
            .maxOfOrNull { it.occurredAt }
        val adjustmentMax = adjustments.asSequence()
            .filter { it.deletedAt == null && it.accountId == accountId }
            .maxOfOrNull { it.occurredAt }
        AccountActivityMaxima(
            maxActiveOccurredAt = listOfNotNull(cashMax, transferMax, balanceUpdateMax, adjustmentMax).maxOrNull(),
            maxBalanceUpdateOccurredAt = balanceUpdateMax,
        )
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = transactionMutex.withLock {
        transactionInvocationCount++
        val snapshot = synchronized(ledgerLock) { snapshot() }
        try {
            block()
        } catch (throwable: Throwable) {
            synchronized(ledgerLock) { restore(snapshot) }
            throw throwable
        }
    }

    private fun aggregate(
        accounts: List<Account>,
        boundary: Long,
        inclusiveEnd: Boolean,
        excludingBalanceUpdateId: Long?,
    ): Map<Long, AccountLedgerAggregate> {
        if (accounts.isEmpty()) return emptyMap()
        return synchronized(ledgerLock) {
            val accountById = accounts.distinctBy(Account::id).associateBy(Account::id)
            val result = accountById.keys.associateWithTo(linkedMapOf()) { accountId ->
                AccountLedgerAggregate(accountId = accountId)
            }

            fun isIncluded(account: Account, occurredAt: Long): Boolean {
                val openingAt = TimeMath.floorToMinute(account.createdAt)
                val withinEnd = if (inclusiveEnd) occurredAt <= boundary else occurredAt < boundary
                return occurredAt >= openingAt && withinEnd
            }

            cashFlowRecords.asSequence()
                .filter { it.deletedAt == null }
                .forEach { record ->
                    val account = accountById[record.accountId] ?: return@forEach
                    if (!isIncluded(account, record.occurredAt)) return@forEach
                    val current = result.getValue(account.id)
                    result[account.id] = when (record.direction) {
                        CashFlowDirection.INFLOW.value -> current.copy(
                            inflow = ledgerAddExact(current.inflow, record.amount),
                        )

                        CashFlowDirection.OUTFLOW.value -> current.copy(
                            outflow = ledgerAddExact(current.outflow, record.amount),
                        )

                        else -> current
                    }
                }

            transferRecords.asSequence()
                .filter { it.deletedAt == null }
                .forEach { record ->
                    accountById[record.fromAccountId]?.let { account ->
                        if (isIncluded(account, record.occurredAt)) {
                            val current = result.getValue(account.id)
                            result[account.id] = current.copy(
                                transferOut = ledgerAddExact(current.transferOut, record.amount),
                            )
                        }
                    }
                    accountById[record.toAccountId]?.let { account ->
                        if (isIncluded(account, record.occurredAt)) {
                            val current = result.getValue(account.id)
                            result[account.id] = current.copy(
                                transferIn = ledgerAddExact(current.transferIn, record.amount),
                            )
                        }
                    }
                }

            adjustments.asSequence()
                .filter { it.deletedAt == null }
                .forEach { record ->
                    val account = accountById[record.accountId] ?: return@forEach
                    if (!isIncluded(account, record.occurredAt)) return@forEach
                    val current = result.getValue(account.id)
                    result[account.id] = current.copy(
                        manualAdjustment = ledgerAddExact(current.manualAdjustment, record.delta),
                    )
                }

            balanceUpdates.asSequence()
                .filter { it.deletedAt == null && it.id != excludingBalanceUpdateId }
                .forEach { record ->
                    val account = accountById[record.accountId] ?: return@forEach
                    if (!isIncluded(account, record.occurredAt)) return@forEach
                    val current = result.getValue(account.id)
                    result[account.id] = current.copy(
                        reconciliation = ledgerAddExact(current.reconciliation, record.delta),
                    )
                }

            result
        }
    }

    override suspend fun insertCashFlowRecord(record: CashFlowRecord): LedgerInsertResult = synchronized(ledgerLock) {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        val normalized = record.copy(note = record.note.trim())
        cashFlowRecords.firstOrNull { it.operationId == normalized.operationId }?.let { existing ->
            return@synchronized cashFlowReplayResult(existing, normalized)
        }
        val id = if (record.id == 0L) {
            nextCashFlowId++
        } else {
            if (cashFlowRecords.any { it.id == record.id }) {
                throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, record.id)
            }
            nextCashFlowId = nextIdAfterExplicit(record.id, nextCashFlowId)
            record.id
        }
        cashFlowRecords += normalized.copy(id = id)
        bumpVersion()
        LedgerInsertResult(recordId = id, inserted = true)
    }

    override suspend fun updateCashFlowRecord(
        record: CashFlowRecord,
        expectedUpdatedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        val index = cashFlowRecords.indexOfFirst { it.id == record.id }
        if (index < 0) return@synchronized false
        val existing = cashFlowRecords[index]
        if (existing.deletedAt != null ||
            existing.operationId != record.operationId ||
            existing.updatedAt != expectedUpdatedAt
        ) {
            return@synchronized false
        }
        cashFlowRecords[index] = record.copy(
            id = existing.id,
            note = record.note,
            createdAt = existing.createdAt,
            operationId = existing.operationId,
            deletedAt = null,
        )
        bumpVersion()
        true
    }

    override suspend fun softDeleteCashFlowRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = cashFlowRecords.indexOfFirst {
            it.id == id &&
                it.operationId == operationId &&
                it.updatedAt == expectedUpdatedAt &&
                it.deletedAt == null
        }
        if (index < 0) return@synchronized false
        cashFlowRecords[index] = cashFlowRecords[index].copy(deletedAt = deletedAt, updatedAt = deletedAt)
        bumpVersion()
        true
    }

    override suspend fun restoreCashFlowRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = cashFlowRecords.indexOfFirst {
            it.id == id && it.operationId == operationId && it.deletedAt == expectedDeletedAt
        }
        if (index < 0) return@synchronized false
        cashFlowRecords[index] = cashFlowRecords[index].copy(deletedAt = null, updatedAt = restoredAt)
        bumpVersion()
        true
    }

    override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? {
        return cashFlowRecords.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun queryStoredCashFlowRecordById(id: Long): CashFlowRecord? =
        synchronized(ledgerLock) { cashFlowRecords.firstOrNull { it.id == id } }

    override suspend fun queryCashFlowRecordByOperationId(operationId: String): CashFlowRecord? {
        return cashFlowRecords.firstOrNull { it.operationId == operationId }
    }

    override suspend fun queryAllCashFlowRecords(): List<CashFlowRecord> {
        return cashFlowRecords.toList()
    }

    override suspend fun queryAllActiveCashFlowRecords(): List<CashFlowRecord> {
        return cashFlowRecords.filter { it.deletedAt == null }
    }

    override suspend fun queryCashFlowRecordsByAccountId(accountId: Long): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords().filter { it.accountId == accountId }
    }

    override suspend fun queryRecentCashFlowNotes(direction: String, accountId: Long?, limit: Int): List<String> {
        return queryAllActiveCashFlowRecords()
            .asSequence()
            .filter { it.direction == direction }
            .filter { accountId == null || it.accountId == accountId }
            .filter { it.note.isNotBlank() }
            .sortedWith(compareByDescending<CashFlowRecord> { it.occurredAt }.thenByDescending { it.id })
            .map { it.note }
            .distinct()
            .take(limit)
            .toList()
    }

    override suspend fun queryActiveCashFlowRecordsByDirectionBetween(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == direction && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .sortedWith(compareBy<CashFlowRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertTransferRecord(record: TransferRecord): LedgerInsertResult = synchronized(ledgerLock) {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        val normalized = record.copy(note = record.note.trim())
        transferRecords.firstOrNull { it.operationId == normalized.operationId }?.let { existing ->
            return@synchronized transferReplayResult(existing, normalized)
        }
        val id = if (record.id == 0L) {
            nextTransferId++
        } else {
            if (transferRecords.any { it.id == record.id }) {
                throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, record.id)
            }
            nextTransferId = nextIdAfterExplicit(record.id, nextTransferId)
            record.id
        }
        transferRecords += normalized.copy(id = id)
        bumpVersion()
        LedgerInsertResult(recordId = id, inserted = true)
    }

    override suspend fun updateTransferRecord(
        record: TransferRecord,
        expectedUpdatedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        val index = transferRecords.indexOfFirst { it.id == record.id }
        if (index < 0) return@synchronized false
        val existing = transferRecords[index]
        if (existing.deletedAt != null ||
            existing.operationId != record.operationId ||
            existing.updatedAt != expectedUpdatedAt
        ) {
            return@synchronized false
        }
        transferRecords[index] = record.copy(
            id = existing.id,
            note = record.note,
            createdAt = existing.createdAt,
            operationId = existing.operationId,
            deletedAt = null,
        )
        bumpVersion()
        true
    }

    override suspend fun softDeleteTransferRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = transferRecords.indexOfFirst {
            it.id == id &&
                it.operationId == operationId &&
                it.updatedAt == expectedUpdatedAt &&
                it.deletedAt == null
        }
        if (index < 0) return@synchronized false
        transferRecords[index] = transferRecords[index].copy(deletedAt = deletedAt, updatedAt = deletedAt)
        bumpVersion()
        true
    }

    override suspend fun restoreTransferRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = transferRecords.indexOfFirst {
            it.id == id && it.operationId == operationId && it.deletedAt == expectedDeletedAt
        }
        if (index < 0) return@synchronized false
        transferRecords[index] = transferRecords[index].copy(deletedAt = null, updatedAt = restoredAt)
        bumpVersion()
        true
    }

    override suspend fun queryTransferRecordById(id: Long): TransferRecord? {
        return transferRecords.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun queryStoredTransferRecordById(id: Long): TransferRecord? =
        synchronized(ledgerLock) { transferRecords.firstOrNull { it.id == id } }

    override suspend fun queryTransferRecordByOperationId(operationId: String): TransferRecord? {
        return transferRecords.firstOrNull { it.operationId == operationId }
    }

    override suspend fun queryAllTransferRecords(): List<TransferRecord> {
        return transferRecords.toList()
    }

    override suspend fun queryAllActiveTransferRecords(): List<TransferRecord> {
        return transferRecords.filter { it.deletedAt == null }
    }

    override suspend fun queryActiveTransferRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransferRecord> {
        return queryAllActiveTransferRecords()
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .sortedWith(compareBy<TransferRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryTransferPathTotalsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransferPathTotal> = queryActiveTransferRecordsBetween(startInclusive, endExclusive)
        .groupBy { it.fromAccountId to it.toAccountId }
        .map { (path, records) ->
            TransferPathTotal(path.first, path.second, records.map(TransferRecord::amount).ledgerSumExact())
        }
        .sortedWith(
            compareByDescending<TransferPathTotal> { it.amount }
                .thenBy { it.fromAccountId }
                .thenBy { it.toAccountId },
        )

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

    override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): LedgerInsertResult = synchronized(ledgerLock) {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        balanceUpdates.firstOrNull { it.operationId == record.operationId }?.let { existing ->
            return@synchronized balanceUpdateReplayResult(existing, record)
        }
        val id = if (record.id == 0L) {
            nextBalanceUpdateId++
        } else {
            if (balanceUpdates.any { it.id == record.id }) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, record.id)
            }
            nextBalanceUpdateId = nextIdAfterExplicit(record.id, nextBalanceUpdateId)
            record.id
        }
        balanceUpdates += record.copy(id = id)
        bumpVersion()
        LedgerInsertResult(recordId = id, inserted = true)
    }

    override suspend fun updateBalanceUpdateRecord(
        record: BalanceUpdateRecord,
        expectedUpdatedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        val index = balanceUpdates.indexOfFirst { it.id == record.id }
        if (index < 0) return@synchronized false
        val existing = balanceUpdates[index]
        if (existing.deletedAt != null ||
            existing.operationId != record.operationId ||
            existing.updatedAt != expectedUpdatedAt
        ) {
            return@synchronized false
        }
        balanceUpdates[index] = record.copy(
            id = existing.id,
            createdAt = existing.createdAt,
            operationId = existing.operationId,
            deletedAt = null,
        )
        bumpVersion()
        true
    }

    override suspend fun softDeleteBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = balanceUpdates.indexOfFirst {
            it.id == id &&
                it.operationId == operationId &&
                it.updatedAt == expectedUpdatedAt &&
                it.deletedAt == null
        }
        if (index < 0) return@synchronized false
        balanceUpdates[index] = balanceUpdates[index].copy(deletedAt = deletedAt, updatedAt = deletedAt)
        bumpVersion()
        true
    }

    override suspend fun restoreBalanceUpdateRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = balanceUpdates.indexOfFirst {
            it.id == id && it.operationId == operationId && it.deletedAt == expectedDeletedAt
        }
        if (index < 0) return@synchronized false
        balanceUpdates[index] = balanceUpdates[index].copy(deletedAt = null, updatedAt = restoredAt)
        bumpVersion()
        true
    }

    override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? {
        return balanceUpdates.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun queryStoredBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? =
        synchronized(ledgerLock) { balanceUpdates.firstOrNull { it.id == id } }

    override suspend fun queryBalanceUpdateRecordByOperationId(operationId: String): BalanceUpdateRecord? {
        return balanceUpdates.firstOrNull { it.operationId == operationId }
    }

    override suspend fun queryAllBalanceUpdateRecords(): List<BalanceUpdateRecord> {
        return balanceUpdates.toList()
    }

    override suspend fun queryBalanceUpdateRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<BalanceUpdateRecord> {
        return balanceUpdates
            .filter { it.deletedAt == null }
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .sortedWith(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceUpdateRecordsByAccountId(accountId: Long): List<BalanceUpdateRecord> {
        return balanceUpdates
            .filter { it.accountId == accountId && it.deletedAt == null }
            .sortedWith(compareByDescending<BalanceUpdateRecord> { it.occurredAt }.thenByDescending { it.id })
    }

    override suspend fun getLatestBalanceUpdate(accountId: Long): BalanceUpdateRecord? {
        return queryBalanceUpdateRecordsByAccountId(accountId)
            .maxWithOrNull(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun insertBalanceAdjustmentRecord(record: BalanceAdjustmentRecord): LedgerInsertResult = synchronized(ledgerLock) {
        requireInsertableId(record.id)
        requireOperationId(record.operationId)
        adjustments.firstOrNull { it.operationId == record.operationId }?.let { existing ->
            return@synchronized balanceAdjustmentReplayResult(existing, record)
        }
        val id = if (record.id == 0L) {
            nextAdjustmentId++
        } else {
            if (adjustments.any { it.id == record.id }) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_ADJUSTMENT, record.id)
            }
            nextAdjustmentId = nextIdAfterExplicit(record.id, nextAdjustmentId)
            record.id
        }
        adjustments += record.copy(id = id)
        bumpVersion()
        LedgerInsertResult(recordId = id, inserted = true)
    }

    override suspend fun updateBalanceAdjustmentRecord(
        record: BalanceAdjustmentRecord,
        expectedUpdatedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        requireNewerTimestamp(record.updatedAt, expectedUpdatedAt)
        val index = adjustments.indexOfFirst { it.id == record.id }
        if (index < 0) return@synchronized false
        val existing = adjustments[index]
        if (existing.deletedAt != null ||
            existing.operationId != record.operationId ||
            existing.updatedAt != expectedUpdatedAt
        ) {
            return@synchronized false
        }
        adjustments[index] = record.copy(
            id = existing.id,
            createdAt = existing.createdAt,
            operationId = existing.operationId,
            deletedAt = null,
        )
        bumpVersion()
        true
    }

    override suspend fun softDeleteBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = adjustments.indexOfFirst {
            it.id == id &&
                it.operationId == operationId &&
                it.updatedAt == expectedUpdatedAt &&
                it.deletedAt == null
        }
        if (index < 0) return@synchronized false
        adjustments[index] = adjustments[index].copy(deletedAt = deletedAt, updatedAt = deletedAt)
        bumpVersion()
        true
    }

    override suspend fun restoreBalanceAdjustmentRecord(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Boolean = synchronized(ledgerLock) {
        val index = adjustments.indexOfFirst {
            it.id == id && it.operationId == operationId && it.deletedAt == expectedDeletedAt
        }
        if (index < 0) return@synchronized false
        adjustments[index] = adjustments[index].copy(deletedAt = null, updatedAt = restoredAt)
        bumpVersion()
        true
    }

    override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? {
        return adjustments.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun queryStoredBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? =
        synchronized(ledgerLock) { adjustments.firstOrNull { it.id == id } }

    override suspend fun queryBalanceAdjustmentRecordByOperationId(operationId: String): BalanceAdjustmentRecord? {
        return adjustments.firstOrNull { it.operationId == operationId }
    }

    override suspend fun queryAllBalanceAdjustmentRecords(): List<BalanceAdjustmentRecord> {
        return adjustments.toList()
    }

    override suspend fun queryBalanceAdjustmentRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<BalanceAdjustmentRecord> {
        return adjustments
            .filter { it.deletedAt == null }
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .sortedWith(compareBy<BalanceAdjustmentRecord> { it.occurredAt }.thenBy { it.id })
    }

    override suspend fun queryBalanceAdjustmentRecordsByAccountId(accountId: Long): List<BalanceAdjustmentRecord> {
        return adjustments
            .filter { it.accountId == accountId && it.deletedAt == null }
            .sortedWith(compareByDescending<BalanceAdjustmentRecord> { it.occurredAt }.thenByDescending { it.id })
    }

    override suspend fun sumInflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter {
                it.direction == CashFlowDirection.INFLOW.value &&
                    it.occurredAt.isInRange(startInclusive, endExclusive)
            }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumOutflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long {
        return queryCashFlowRecordsByAccountId(accountId)
            .filter {
                it.direction == CashFlowDirection.OUTFLOW.value &&
                    it.occurredAt.isInRange(startInclusive, endExclusive)
            }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumTransferInBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.toAccountId == accountId && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumTransferOutBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long {
        return queryTransferRecordsByAccountId(accountId)
            .filter { it.fromAccountId == accountId && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumAdjustmentBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long {
        return queryBalanceAdjustmentRecordsByAccountId(accountId)
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { it.delta }
            .ledgerSumExact()
    }

    override suspend fun sumCashInflowBetween(startInclusive: Long, endExclusive: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter {
                it.direction == CashFlowDirection.INFLOW.value &&
                    it.occurredAt.isInRange(startInclusive, endExclusive)
            }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumCashOutflowBetween(startInclusive: Long, endExclusive: Long): Long {
        return queryAllActiveCashFlowRecords()
            .filter {
                it.direction == CashFlowDirection.OUTFLOW.value &&
                    it.occurredAt.isInRange(startInclusive, endExclusive)
            }
            .map { it.amount }
            .ledgerSumExact()
    }

    override suspend fun sumBalanceUpdateIncreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceUpdates
            .filter { it.deletedAt == null }
            .filter { it.delta > 0 && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { it.delta }
            .ledgerSumExact()

    override suspend fun sumBalanceUpdateDecreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        balanceUpdates
            .filter { it.deletedAt == null }
            .filter { it.delta < 0 && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { ledgerSubtractExact(0L, it.delta) }
            .ledgerSumExact()

    override suspend fun sumManualAdjustmentIncreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        adjustments
            .filter { it.deletedAt == null }
            .filter { it.delta > 0 && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { it.delta }
            .ledgerSumExact()

    override suspend fun sumManualAdjustmentDecreaseBetween(startInclusive: Long, endExclusive: Long): Long =
        adjustments
            .filter { it.deletedAt == null }
            .filter { it.delta < 0 && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .map { ledgerSubtractExact(0L, it.delta) }
            .ledgerSumExact()

    override suspend fun countActiveCashFlowRecordsBetween(startInclusive: Long, endExclusive: Long): Int {
        return queryAllActiveCashFlowRecords()
            .count { it.occurredAt.isInRange(startInclusive, endExclusive) }
    }

    override suspend fun countActiveTransferRecordsBetween(startInclusive: Long, endExclusive: Long): Int {
        return queryAllActiveTransferRecords()
            .count { it.occurredAt.isInRange(startInclusive, endExclusive) }
    }

    override suspend fun countManualAdjustmentRecordsBetween(startInclusive: Long, endExclusive: Long): Int {
        return adjustments.count { it.deletedAt == null && it.occurredAt.isInRange(startInclusive, endExclusive) }
    }

    override suspend fun queryHomePeriodLedgerSummary(
        startInclusive: Long,
        endExclusive: Long,
    ): HomePeriodLedgerSummary = synchronized(ledgerLock) {
        homePeriodSummaryInvocationCount++
        val cash = cashFlowRecords.filter {
            it.deletedAt == null && it.occurredAt.isInRange(startInclusive, endExclusive)
        }
        val periodTransfers = transferRecords.filter {
            it.deletedAt == null && it.occurredAt.isInRange(startInclusive, endExclusive)
        }
        val reconciliations = balanceUpdates.filter {
            it.deletedAt == null && it.occurredAt.isInRange(startInclusive, endExclusive)
        }
        val manualAdjustments = adjustments.filter {
            it.deletedAt == null && it.occurredAt.isInRange(startInclusive, endExclusive)
        }
        HomePeriodLedgerSummary(
            cashInflow = cash
                .filter { it.direction == CashFlowDirection.INFLOW.value }
                .map { it.amount }
                .ledgerSumExact(),
            cashOutflow = cash
                .filter { it.direction == CashFlowDirection.OUTFLOW.value }
                .map { it.amount }
                .ledgerSumExact(),
            reconciliationIncrease = reconciliations
                .filter { it.delta > 0L }
                .map { it.delta }
                .ledgerSumExact(),
            reconciliationDecrease = reconciliations
                .filter { it.delta < 0L }
                .map { ledgerSubtractExact(0L, it.delta) }
                .ledgerSumExact(),
            manualAdjustmentIncrease = manualAdjustments
                .filter { it.delta > 0L }
                .map { it.delta }
                .ledgerSumExact(),
            manualAdjustmentDecrease = manualAdjustments
                .filter { it.delta < 0L }
                .map { ledgerSubtractExact(0L, it.delta) }
                .ledgerSumExact(),
            cashFlowRecordCount = cash.size,
            transferRecordCount = periodTransfers.size,
            manualAdjustmentRecordCount = manualAdjustments.size,
        )
    }

    override suspend fun queryActiveCashFlowRecordsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecord> {
        return queryAllActiveCashFlowRecords()
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .sortedBy { it.occurredAt }
    }

    override suspend fun queryCashFlowAnalysisEntriesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowAnalysisEntry> = queryActiveCashFlowRecordsBetween(startInclusive, endExclusive)
        .map { CashFlowAnalysisEntry(it.accountId, it.direction, it.amount, it.occurredAt) }

    override suspend fun queryPurposeTotals(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PurposeTotal> {
        return queryAllActiveCashFlowRecords()
            .filter { it.direction == direction && it.occurredAt.isInRange(startInclusive, endExclusive) }
            .groupBy { it.note.ifBlank { "未填写用途" } }
            .map { (purpose, records) ->
                PurposeTotal(
                    purpose = purpose,
                    amount = records.map(CashFlowRecord::amount).ledgerSumExact(),
                )
            }
            .sortedByDescending { it.amount }
    }

    override suspend fun queryDailyCashFlowTotals(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotal> {
        val offset = ZoneOffset.ofTotalSeconds(zoneOffsetSeconds)
        return queryAllActiveCashFlowRecords()
            .filter { it.occurredAt.isInRange(startInclusive, endExclusive) }
            .groupBy { record ->
                val epochDay = Instant.ofEpochMilli(record.occurredAt).atOffset(offset).toLocalDate().toEpochDay()
                epochDay to record.direction
            }
            .map { (key, records) ->
                CashFlowDailyTotal(
                    epochDay = key.first,
                    direction = key.second,
                    amount = records.map(CashFlowRecord::amount).ledgerSumExact(),
                )
            }
            .sortedWith(compareBy<CashFlowDailyTotal> { it.epochDay }.thenBy { it.direction })
    }

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        filters.requireValidAmountBounds()
        return buildHistoryRecords()
            .asSequence()
            .filter { it.matches(filters) }
            .filter { it.isAfterCursor(cursor) }
            .take(limit)
            .toList()
    }

    override suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int {
        filters.requireValidAmountBounds()
        return buildHistoryRecords().count { it.matches(filters) }
    }

    private fun buildHistoryRecords(): List<HistoryRecord> {
        val cashRecords = cashFlowRecords.filter { it.deletedAt == null }.map { record ->
            val title = record.note.ifBlank { "未填写备注" }
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.CASH_FLOW,
                sourceOrder = 4,
                accountId = record.accountId,
                relatedAccountId = null,
                title = title,
                amount = if (record.direction == CashFlowDirection.INFLOW.value) record.amount else -record.amount,
                occurredAt = record.occurredAt,
                keywordSource = listOfNotNull(record.note, title, accountNameLookup(record.accountId)).joinToString(" "),
            )
        }
        val transferHistoryRecords = transferRecords.filter { it.deletedAt == null }.map { record ->
            val title = record.note.ifBlank { "账户间转移" }
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.TRANSFER,
                sourceOrder = 3,
                accountId = record.fromAccountId,
                relatedAccountId = record.toAccountId,
                title = title,
                amount = record.amount,
                occurredAt = record.occurredAt,
                keywordSource = listOfNotNull(
                    record.note,
                    title,
                    "转账",
                    accountNameLookup(record.fromAccountId),
                    accountNameLookup(record.toAccountId),
                ).joinToString(" "),
            )
        }
        val updateHistoryRecords = balanceUpdates.filter { it.deletedAt == null }.map { record ->
            val title = if (record.delta == 0L) "余额核对" else "对账调整"
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.BALANCE_UPDATE,
                sourceOrder = 2,
                accountId = record.accountId,
                relatedAccountId = null,
                title = title,
                amount = record.delta,
                occurredAt = record.occurredAt,
                keywordSource = listOfNotNull(title, accountNameLookup(record.accountId)).joinToString(" "),
            )
        }
        val adjustmentHistoryRecords = adjustments.filter { it.deletedAt == null }.map { record ->
            val title = "余额矫正"
            HistoryRecord(
                recordId = record.id,
                type = HistoryRecordType.BALANCE_ADJUSTMENT,
                sourceOrder = 1,
                accountId = record.accountId,
                relatedAccountId = null,
                title = title,
                amount = record.delta,
                occurredAt = record.occurredAt,
                keywordSource = listOfNotNull(title, "余额校正", accountNameLookup(record.accountId)).joinToString(" "),
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
        val keyword = normalizeHistorySearchText(filters.keyword.trim())
        val excludeKeyword = normalizeHistorySearchText(filters.excludeKeyword.trim())
        val source = normalizeHistorySearchText(keywordSource)
        val keywordOk = keyword.isBlank() || source.contains(keyword)
        val excludeOk = excludeKeyword.isBlank() || !source.contains(excludeKeyword)
        val accountOk = filters.accountId == null ||
            accountId == filters.accountId ||
            relatedAccountId == filters.accountId
        val transferFromOk = filters.transferFromAccountId == null ||
            (type == HistoryRecordType.TRANSFER && accountId == filters.transferFromAccountId)
        val transferToOk = filters.transferToAccountId == null ||
            (type == HistoryRecordType.TRANSFER && relatedAccountId == filters.transferToAccountId)
        val startOk = filters.dateStartAt == null || occurredAt >= filters.dateStartAt
        val endOk = filters.dateEndAt == null || occurredAt < filters.dateEndAt
        val minOk = filters.minAmount == null || amount >= filters.minAmount || amount <= -filters.minAmount
        val maxOk = filters.maxAmount == null || (amount >= -filters.maxAmount && amount <= filters.maxAmount)
        val directionOk = when (filters.amountDirection) {
            HistoryAmountDirection.ALL -> true
            HistoryAmountDirection.INCREASE -> amount > 0 && type != HistoryRecordType.TRANSFER
            HistoryAmountDirection.DECREASE -> amount < 0 && type != HistoryRecordType.TRANSFER
        }
        val typeOk = filters.recordTypes.isEmpty() || type in filters.recordTypes
        return keywordOk && excludeOk && typeOk && accountOk && transferFromOk && transferToOk &&
            startOk && endOk && minOk && maxOk && directionOk
    }

    private fun HistoryRecord.isAfterCursor(cursor: HistoryPageCursor?): Boolean {
        return cursor == null ||
            occurredAt < cursor.occurredAt ||
            (occurredAt == cursor.occurredAt && sourceOrder < cursor.sourceOrder) ||
            (occurredAt == cursor.occurredAt && sourceOrder == cursor.sourceOrder && recordId < cursor.recordId)
    }

    private fun Long.isInRange(startInclusive: Long, endExclusive: Long): Boolean =
        this >= startInclusive && this < endExclusive

    private fun requireOperationId(operationId: String) {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
    }

    private fun requireInsertableId(id: Long) {
        require(id == 0L) { "新建账本记录的 ID 必须为 0" }
    }

    private fun requireNewerTimestamp(updatedAt: Long, expectedUpdatedAt: Long) {
        require(updatedAt > expectedUpdatedAt) { "新的更新时间必须晚于原记录" }
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
        return replayResult(samePayload, LedgerRecordKind.CASH_FLOW, requested.operationId, existing.id)
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
        return replayResult(samePayload, LedgerRecordKind.TRANSFER, requested.operationId, existing.id)
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
        return replayResult(samePayload, LedgerRecordKind.BALANCE_UPDATE, requested.operationId, existing.id)
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
        return replayResult(samePayload, LedgerRecordKind.BALANCE_ADJUSTMENT, requested.operationId, existing.id)
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

    private fun bumpVersion() {
        changeVersion.value = changeVersion.value + 1
    }

    private fun nextIdAfterExplicit(explicitId: Long, currentNextId: Long): Long {
        if (explicitId < currentNextId) return currentNextId
        check(explicitId != Long.MAX_VALUE) { "Record id cannot advance beyond Long.MAX_VALUE" }
        return explicitId + 1
    }

    private fun snapshot() = LedgerSnapshot(
        cashFlowRecords = cashFlowRecords.toList(),
        transferRecords = transferRecords.toList(),
        balanceUpdates = balanceUpdates.toList(),
        adjustments = adjustments.toList(),
        nextCashFlowId = nextCashFlowId,
        nextTransferId = nextTransferId,
        nextBalanceUpdateId = nextBalanceUpdateId,
        nextAdjustmentId = nextAdjustmentId,
        changeVersion = changeVersion.value,
    )

    private fun restore(snapshot: LedgerSnapshot) {
        cashFlowRecords.clear()
        cashFlowRecords.addAll(snapshot.cashFlowRecords)
        transferRecords.clear()
        transferRecords.addAll(snapshot.transferRecords)
        balanceUpdates.clear()
        balanceUpdates.addAll(snapshot.balanceUpdates)
        adjustments.clear()
        adjustments.addAll(snapshot.adjustments)
        nextCashFlowId = snapshot.nextCashFlowId
        nextTransferId = snapshot.nextTransferId
        nextBalanceUpdateId = snapshot.nextBalanceUpdateId
        nextAdjustmentId = snapshot.nextAdjustmentId
        changeVersion.value = snapshot.changeVersion
    }

    private data class LedgerSnapshot(
        val cashFlowRecords: List<CashFlowRecord>,
        val transferRecords: List<TransferRecord>,
        val balanceUpdates: List<BalanceUpdateRecord>,
        val adjustments: List<BalanceAdjustmentRecord>,
        val nextCashFlowId: Long,
        val nextTransferId: Long,
        val nextBalanceUpdateId: Long,
        val nextAdjustmentId: Long,
        val changeVersion: Long,
    )
}
