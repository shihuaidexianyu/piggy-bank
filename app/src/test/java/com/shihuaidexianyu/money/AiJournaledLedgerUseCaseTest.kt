package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.AiCreateCashFlowCommand
import com.shihuaidexianyu.money.domain.usecase.AiCreateTransferCommand
import com.shihuaidexianyu.money.domain.usecase.AiJournaledLedgerUseCase
import com.shihuaidexianyu.money.domain.usecase.AiMutationIdentity
import com.shihuaidexianyu.money.domain.usecase.AiUpdateCashFlowCommand
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiJournaledLedgerUseCaseTest {
    @Test
    fun consecutiveUpdatesUndoInStrictStackOrderThenUndoCreate() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.createAccount()
        val created = fixture.useCase.createCashFlow(
            fixture.createCommand("create", accountId, amount = 100, note = "初始"),
        )
        val recordId = created.entry.recordId

        fixture.clock.now = 200
        fixture.useCase.updateCashFlow(
            fixture.updateCommand("update-1", recordId, accountId, amount = 200, note = "第一次"),
        )
        fixture.clock.now = 300
        fixture.useCase.updateCashFlow(
            fixture.updateCommand("update-2", recordId, accountId, amount = 300, note = "第二次"),
        )

        fixture.clock.now = 400
        assertIs<UndoLatestAiMutationResult.Undone>(fixture.useCase.undoLatest("undo-2"))
        assertEquals("第一次", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        assertEquals(200, fixture.transactions.queryCashFlowRecordById(recordId)?.amount)

        fixture.clock.now = 500
        assertIs<UndoLatestAiMutationResult.Undone>(fixture.useCase.undoLatest("undo-1"))
        assertEquals("初始", fixture.transactions.queryCashFlowRecordById(recordId)?.note)
        assertEquals(100, fixture.transactions.queryCashFlowRecordById(recordId)?.amount)

        fixture.clock.now = 600
        assertIs<UndoLatestAiMutationResult.Undone>(fixture.useCase.undoLatest("undo-create"))
        assertNull(fixture.transactions.queryCashFlowRecordById(recordId))
        assertNotNull(fixture.transactions.queryStoredCashFlowRecordById(recordId)?.deletedAt)
        assertEquals(0, fixture.journal.appliedEntries().size)
    }

    @Test
    fun semanticConflictStopsUndoWithoutOverwritingLaterManualChange() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.createAccount()
        val receipt = fixture.useCase.createCashFlow(
            fixture.createCommand("create", accountId, amount = 100, note = "AI 写入"),
        )
        val current = assertNotNull(fixture.transactions.queryCashFlowRecordById(receipt.entry.recordId))
        assertTrue(
            fixture.transactions.updateCashFlowRecord(
                current.copy(note = "用户后来修改", updatedAt = current.updatedAt + 1),
                current.updatedAt,
            ),
        )

        val result = fixture.useCase.undoLatest("undo-conflict")

        assertIs<UndoLatestAiMutationResult.Conflict>(result)
        assertEquals("用户后来修改", fixture.transactions.queryCashFlowRecordById(receipt.entry.recordId)?.note)
        assertEquals(AiMutationJournalStatus.APPLIED, fixture.journal.queryLatestApplied()?.status)
    }

    @Test
    fun duplicateRequestIdReplaysReceiptWithoutSecondMutationOrJournalEntry() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.createAccount()
        val command = fixture.createCommand("same-request", accountId, amount = 100, note = "一次")

        val first = fixture.useCase.createCashFlow(command)
        val replay = fixture.useCase.createCashFlow(command)

        assertFalse(first.replayed)
        assertTrue(replay.replayed)
        assertEquals(first.entry.id, replay.entry.id)
        assertEquals(1, fixture.transactions.queryAllActiveCashFlowRecords().size)
        assertEquals(1, fixture.journal.queryRecent(10).size)
    }

    @Test
    fun missingCreateTimesUsePhoneClockWhileExplicitTimeIsPreserved() = runBlocking {
        val fixture = Fixture()
        val cashAccountId = fixture.createAccount()
        val bankAccountId = fixture.accounts.createAccount(
            Account(name = "银行", initialBalance = 0, createdAt = 1),
        )

        fixture.clock.now = 777L
        val cashReceipt = fixture.useCase.createCashFlow(
            fixture.createCommand("phone-time-cash", cashAccountId, amount = 100, note = "手机取时")
                .copy(occurredAt = null),
        )
        val cashRecord = assertNotNull(
            fixture.transactions.queryCashFlowRecordById(cashReceipt.entry.recordId),
        )
        assertEquals(777L, cashRecord.occurredAt)

        fixture.clock.now = 888L
        val transferReceipt = fixture.useCase.createTransfer(
            AiCreateTransferCommand(
                identity = fixture.identity("phone-time-transfer"),
                fromAccountId = cashAccountId,
                toAccountId = bankAccountId,
                amount = 50L,
                note = "手机取时",
                occurredAt = null,
            ),
        )
        val transferRecord = assertNotNull(
            fixture.transactions.queryTransferRecordById(transferReceipt.entry.recordId),
        )
        assertEquals(888L, transferRecord.occurredAt)

        fixture.clock.now = 999L
        val explicitReceipt = fixture.useCase.createCashFlow(
            fixture.createCommand("explicit-time", cashAccountId, amount = 10, note = "显式时间"),
        )
        assertEquals(
            10L,
            fixture.transactions.queryCashFlowRecordById(explicitReceipt.entry.recordId)?.occurredAt,
        )
    }

    @Test
    fun deleteCanBeUndoneAndRepeatedUndoRequestIsIdempotent() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.createAccount()
        val created = fixture.useCase.createCashFlow(
            fixture.createCommand("create", accountId, amount = 100, note = "保留"),
        )
        fixture.clock.now = 200
        fixture.useCase.deleteCashFlow(
            identity = fixture.identity("delete"),
            recordId = created.entry.recordId,
        )
        assertNull(fixture.transactions.queryCashFlowRecordById(created.entry.recordId))

        fixture.clock.now = 300
        val firstUndo = assertIs<UndoLatestAiMutationResult.Undone>(fixture.useCase.undoLatest("undo-delete"))
        val replayedUndo = assertIs<UndoLatestAiMutationResult.Undone>(fixture.useCase.undoLatest("undo-delete"))

        assertFalse(firstUndo.replayed)
        assertTrue(replayedUndo.replayed)
        assertEquals("保留", fixture.transactions.queryCashFlowRecordById(created.entry.recordId)?.note)
        assertEquals(created.entry.id, fixture.journal.queryLatestApplied()?.id)
    }

    private class Fixture {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val journal = FakeJournalRepository()
        val clock = MutableClock(100)

        private val transactionPort = object : TransactionRepository by transactions {
            // The production Room runner is re-entrant. The in-memory repository intentionally is
            // not, so flatten nested use-case transactions in this focused coordinator test.
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        private val refresh = RefreshAccountActivityStateUseCase(
            accountRepository = accounts,
            ledgerAggregateRepository = transactions,
        )
        private val createCash = CreateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock)
        private val updateCash = UpdateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock)
        private val deleteCash = DeleteCashFlowRecordUseCase(accounts, transactionPort, refresh, clock)
        private val createTransfer = CreateTransferRecordUseCase(accounts, transactionPort, refresh, clock)
        private val updateTransfer = UpdateTransferRecordUseCase(accounts, transactionPort, refresh, clock)
        private val deleteTransfer = DeleteTransferRecordUseCase(accounts, transactionPort, refresh, clock)
        private val restore = RestoreLedgerRecordUseCase(accounts, transactionPort, refresh, clock)

        val useCase = AiJournaledLedgerUseCase(
            journalRepository = journal,
            transactionRepository = transactionPort,
            createCashFlowRecordUseCase = createCash,
            updateCashFlowRecordUseCase = updateCash,
            deleteCashFlowRecordUseCase = deleteCash,
            createTransferRecordUseCase = createTransfer,
            updateTransferRecordUseCase = updateTransfer,
            deleteTransferRecordUseCase = deleteTransfer,
            restoreLedgerRecordUseCase = restore,
            clockProvider = clock,
        )

        suspend fun createAccount(): Long = accounts.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 1),
        )

        fun identity(requestId: String) = AiMutationIdentity(
            requestId = requestId,
            sessionId = "session",
            clientName = "test-client",
        )

        fun createCommand(
            requestId: String,
            accountId: Long,
            amount: Long,
            note: String,
        ) = AiCreateCashFlowCommand(
            identity = identity(requestId),
            accountId = accountId,
            direction = CashFlowDirection.INFLOW,
            amount = amount,
            note = note,
            occurredAt = 10,
        )

        fun updateCommand(
            requestId: String,
            recordId: Long,
            accountId: Long,
            amount: Long,
            note: String,
        ) = AiUpdateCashFlowCommand(
            identity = identity(requestId),
            recordId = recordId,
            accountId = accountId,
            direction = CashFlowDirection.INFLOW,
            amount = amount,
            note = note,
            occurredAt = 10,
        )
    }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    private class FakeJournalRepository : AiMutationJournalRepository {
        private val entries = MutableStateFlow<List<AiMutationJournalEntry>>(emptyList())
        private var nextId = 1L

        override fun observeRecent(limit: Int): Flow<List<AiMutationJournalEntry>> =
            entries.map { it.sortedByDescending(AiMutationJournalEntry::id).take(limit) }

        override fun observeAppliedCount(): Flow<Int> = entries.map { rows ->
            rows.count { it.status == AiMutationJournalStatus.APPLIED }
        }

        override fun observeLatestApplied(): Flow<AiMutationJournalEntry?> =
            entries.map { rows -> rows.filter { it.status == AiMutationJournalStatus.APPLIED }.maxByOrNull { it.id } }

        override suspend fun queryRecent(limit: Int): List<AiMutationJournalEntry> =
            entries.value.sortedByDescending(AiMutationJournalEntry::id).take(limit)

        override suspend fun queryByRequestId(requestId: String): AiMutationJournalEntry? =
            entries.value.firstOrNull { it.requestId == requestId }

        override suspend fun queryByUndoRequestId(requestId: String): AiMutationJournalEntry? =
            entries.value.firstOrNull { it.undoRequestId == requestId }

        override suspend fun queryLatestApplied(): AiMutationJournalEntry? = appliedEntries().maxByOrNull { it.id }

        override suspend fun insert(entry: AiMutationJournalEntry): Long {
            check(entries.value.none { it.requestId == entry.requestId })
            val id = nextId++
            entries.value = entries.value + entry.copy(id = id)
            return id
        }

        override suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Boolean {
            val existing = entries.value.firstOrNull { it.id == id } ?: return false
            if (existing.status != AiMutationJournalStatus.APPLIED) return false
            entries.value = entries.value.map {
                if (it.id == id) {
                    it.copy(
                        status = AiMutationJournalStatus.UNDONE,
                        resolvedAt = undoneAt,
                        undoRequestId = undoRequestId,
                    )
                } else {
                    it
                }
            }
            return true
        }

        override suspend fun discardLatest(id: Long, discardedAt: Long): Boolean {
            if (queryLatestApplied()?.id != id) return false
            entries.value = entries.value.map {
                if (it.id == id) {
                    it.copy(status = AiMutationJournalStatus.DISCARDED, resolvedAt = discardedAt)
                } else {
                    it
                }
            }
            return true
        }

        override suspend fun deleteAll() {
            entries.value = emptyList()
        }

        fun appliedEntries(): List<AiMutationJournalEntry> =
            entries.value.filter { it.status == AiMutationJournalStatus.APPLIED }
    }
}
