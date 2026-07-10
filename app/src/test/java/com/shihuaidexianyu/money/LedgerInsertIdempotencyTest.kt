package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LedgerInsertIdempotencyTest {
    @Test
    fun `cash insert is idempotent including normalized note and tombstones`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val original = cash(operationId = "cash-command", note = "  工资  ")

        val first = repository.insertCashFlowRecord(original)
        val replay = repository.insertCashFlowRecord(
            original.copy(id = 999, note = "工资", createdAt = 999, updatedAt = 999),
        )

        assertEquals(LedgerInsertResult(recordId = first.recordId, inserted = true), first)
        assertEquals(LedgerInsertResult(recordId = first.recordId, inserted = false), replay)

        val conflict = assertFailsWith<LedgerOperationConflictException> {
            repository.insertCashFlowRecord(original.copy(amount = original.amount + 1))
        }
        assertEquals(LedgerRecordKind.CASH_FLOW, conflict.kind)
        assertEquals(original.operationId, conflict.operationId)
        assertEquals(first.recordId, conflict.existingRecordId)

        val distinct = repository.insertCashFlowRecord(original.copy(operationId = "cash-command-2"))
        assertTrue(distinct.inserted)
        assertEquals(2, repository.queryAllCashFlowRecords().size)

        repository.softDeleteCashFlowRecord(first.recordId, updatedAt = 500)
        val tombstoneReplay = repository.insertCashFlowRecord(original.copy(note = "工资"))
        assertFalse(tombstoneReplay.inserted)
        assertEquals(first.recordId, tombstoneReplay.recordId)
        assertEquals(500, repository.queryCashFlowRecordByOperationId(original.operationId)?.deletedAt)
    }

    @Test
    fun `transfer insert is idempotent including normalized note and tombstones`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val original = transfer(operationId = "transfer-command", note = "  调拨  ")

        val first = repository.insertTransferRecord(original)
        val replay = repository.insertTransferRecord(
            original.copy(id = 999, note = "调拨", createdAt = 999, updatedAt = 999),
        )

        assertTrue(first.inserted)
        assertFalse(replay.inserted)
        assertEquals(first.recordId, replay.recordId)

        val conflict = assertFailsWith<LedgerOperationConflictException> {
            repository.insertTransferRecord(original.copy(toAccountId = 3))
        }
        assertEquals(LedgerRecordKind.TRANSFER, conflict.kind)
        assertEquals(first.recordId, conflict.existingRecordId)

        assertTrue(repository.insertTransferRecord(original.copy(operationId = "transfer-command-2")).inserted)
        repository.softDeleteTransferRecord(first.recordId, updatedAt = 500)
        val tombstoneReplay = repository.insertTransferRecord(original.copy(note = "调拨"))
        assertFalse(tombstoneReplay.inserted)
        assertEquals(500, repository.queryTransferRecordByOperationId(original.operationId)?.deletedAt)
    }

    @Test
    fun `balance update insert compares caller command and preserves first evidence`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val original = balanceUpdate(operationId = "balance-command")

        val first = repository.insertBalanceUpdateRecord(original)
        val replay = repository.insertBalanceUpdateRecord(
            original.copy(
                id = 999,
                systemBalanceBeforeUpdate = -9_999,
                delta = 9_999,
                createdAt = 999,
                updatedAt = 999,
            ),
        )

        assertTrue(first.inserted)
        assertFalse(replay.inserted)
        assertEquals(first.recordId, replay.recordId)
        val stored = assertNotNull(repository.queryBalanceUpdateRecordByOperationId(original.operationId))
        assertEquals(original.systemBalanceBeforeUpdate, stored.systemBalanceBeforeUpdate)
        assertEquals(original.delta, stored.delta)

        val conflict = assertFailsWith<LedgerOperationConflictException> {
            repository.insertBalanceUpdateRecord(original.copy(actualBalance = original.actualBalance + 1))
        }
        assertEquals(LedgerRecordKind.BALANCE_UPDATE, conflict.kind)

        assertTrue(repository.insertBalanceUpdateRecord(original.copy(operationId = "balance-command-2")).inserted)
        repository.deleteBalanceUpdateRecord(first.recordId, deletedAt = 500)
        val tombstoneReplay = repository.insertBalanceUpdateRecord(original.copy(delta = 123_456))
        assertFalse(tombstoneReplay.inserted)
        assertEquals(500, repository.queryBalanceUpdateRecordByOperationId(original.operationId)?.deletedAt)
    }

    @Test
    fun `adjustment insert is idempotent including tombstones`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val original = adjustment(operationId = "adjustment-command")

        val first = repository.insertBalanceAdjustmentRecord(original)
        val replay = repository.insertBalanceAdjustmentRecord(
            original.copy(id = 999, createdAt = 999, updatedAt = 999),
        )

        assertTrue(first.inserted)
        assertFalse(replay.inserted)
        assertEquals(first.recordId, replay.recordId)

        val conflict = assertFailsWith<LedgerOperationConflictException> {
            repository.insertBalanceAdjustmentRecord(original.copy(delta = original.delta + 1))
        }
        assertEquals(LedgerRecordKind.BALANCE_ADJUSTMENT, conflict.kind)

        assertTrue(repository.insertBalanceAdjustmentRecord(original.copy(operationId = "adjustment-command-2")).inserted)
        repository.deleteBalanceAdjustmentRecord(first.recordId, deletedAt = 500)
        val tombstoneReplay = repository.insertBalanceAdjustmentRecord(original)
        assertFalse(tombstoneReplay.inserted)
        assertEquals(500, repository.queryBalanceAdjustmentRecordByOperationId(original.operationId)?.deletedAt)
    }

    @Test
    fun `concurrent equal inserts converge for every ledger kind`() = runBlocking {
        val repository = InMemoryTransactionRepository()

        assertConcurrentConvergence { repository.insertCashFlowRecord(cash("cash-concurrent")) }
        assertConcurrentConvergence { repository.insertTransferRecord(transfer("transfer-concurrent")) }
        assertConcurrentConvergence { repository.insertBalanceUpdateRecord(balanceUpdate("balance-concurrent")) }
        assertConcurrentConvergence {
            repository.insertBalanceAdjustmentRecord(adjustment("adjustment-concurrent"))
        }

        assertEquals(1, repository.queryAllCashFlowRecords().size)
        assertEquals(1, repository.queryAllTransferRecords().size)
        assertEquals(1, repository.queryAllBalanceUpdateRecords().size)
        assertEquals(1, repository.queryAllBalanceAdjustmentRecords().size)
    }

    @Test
    fun `primary key conflict without matching operation is explicit`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val first = repository.insertCashFlowRecord(cash("first"))

        val failure = assertFailsWith<LedgerRecordChangedException> {
            repository.insertCashFlowRecord(cash("second").copy(id = first.recordId))
        }

        assertEquals(LedgerRecordKind.CASH_FLOW, failure.kind)
        assertEquals(first.recordId, failure.recordId)
        assertEquals(1, repository.queryAllCashFlowRecords().size)
    }

    @Test
    fun `failed in-memory transaction restores all ledger collections ids and version`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val beforeVersion = repository.observeChangeVersion().first()

        assertFailsWith<IllegalStateException> {
            repository.runInTransaction {
                repository.insertCashFlowRecord(cash("rollback-cash"))
                repository.insertTransferRecord(transfer("rollback-transfer"))
                repository.insertBalanceUpdateRecord(balanceUpdate("rollback-update"))
                repository.insertBalanceAdjustmentRecord(adjustment("rollback-adjustment"))
                error("rollback")
            }
        }

        assertTrue(repository.queryAllCashFlowRecords().isEmpty())
        assertTrue(repository.queryAllTransferRecords().isEmpty())
        assertTrue(repository.queryAllBalanceUpdateRecords().isEmpty())
        assertTrue(repository.queryAllBalanceAdjustmentRecords().isEmpty())
        assertEquals(beforeVersion, repository.observeChangeVersion().first())
        assertEquals(1, repository.insertCashFlowRecord(cash("after-rollback")).recordId)
    }

    private suspend fun assertConcurrentConvergence(insert: suspend () -> LedgerInsertResult) {
        val results = coroutineScope {
            List(16) { async(Dispatchers.Default) { insert() } }.awaitAll()
        }
        assertEquals(1, results.map { it.recordId }.distinct().size)
        assertEquals(1, results.count { it.inserted })
    }

    private fun cash(operationId: String, note: String = "工资") = CashFlowRecord(
        accountId = 1,
        direction = "INCOME",
        amount = 10_000,
        note = note,
        occurredAt = 100,
        createdAt = 100,
        updatedAt = 100,
        operationId = operationId,
    )

    private fun transfer(operationId: String, note: String = "调拨") = TransferRecord(
        fromAccountId = 1,
        toAccountId = 2,
        amount = 2_000,
        note = note,
        occurredAt = 100,
        createdAt = 100,
        updatedAt = 100,
        operationId = operationId,
    )

    private fun balanceUpdate(operationId: String) = BalanceUpdateRecord(
        accountId = 1,
        actualBalance = 12_000,
        systemBalanceBeforeUpdate = 10_000,
        delta = 2_000,
        occurredAt = 100,
        createdAt = 100,
        updatedAt = 100,
        operationId = operationId,
    )

    private fun adjustment(operationId: String) = BalanceAdjustmentRecord(
        accountId = 1,
        delta = -500,
        occurredAt = 100,
        createdAt = 100,
        updatedAt = 100,
        operationId = operationId,
    )
}
