package com.shihuaidexianyu.money

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.HistoryRecordDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LedgerInsertIdempotencyTest {
    @Test
    fun `repositories reject explicit ids for all ledger kinds before persistence`() = runBlocking {
        val repositories = listOf(
            transactionRepositoryThatMustNotTouchRoom(),
            InMemoryTransactionRepository(),
        )

        repositories.forEach { repository ->
            val writes = listOf<suspend () -> Unit>(
                { repository.insertCashFlowRecord(cash("explicit-cash").copy(id = 1)) },
                { repository.insertTransferRecord(transfer("explicit-transfer").copy(id = 1)) },
                { repository.insertBalanceUpdateRecord(balanceUpdate("explicit-update").copy(id = 1)) },
                { repository.insertBalanceAdjustmentRecord(adjustment("explicit-adjustment").copy(id = 1)) },
            )

            writes.forEach { write ->
                val failure = assertFailsWith<IllegalArgumentException> { write() }
                assertEquals("新建账本记录的 ID 必须为 0", failure.message)
            }
        }
    }

    @Test
    fun `cash insert is idempotent including normalized note and tombstones`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val original = cash(operationId = "cash-command", note = "  工资  ")

        val first = repository.insertCashFlowRecord(original)
        val replay = repository.insertCashFlowRecord(
            original.copy(note = "工资", createdAt = 999, updatedAt = 999),
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

        repository.softDeleteCurrentCashFlowRecord(first.recordId, updatedAt = 500)
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
            original.copy(note = "调拨", createdAt = 999, updatedAt = 999),
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
        repository.softDeleteCurrentTransferRecord(first.recordId, updatedAt = 500)
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
        repository.softDeleteCurrentBalanceUpdateRecord(first.recordId, deletedAt = 500)
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
            original.copy(createdAt = 999, updatedAt = 999),
        )

        assertTrue(first.inserted)
        assertFalse(replay.inserted)
        assertEquals(first.recordId, replay.recordId)

        val conflict = assertFailsWith<LedgerOperationConflictException> {
            repository.insertBalanceAdjustmentRecord(original.copy(delta = original.delta + 1))
        }
        assertEquals(LedgerRecordKind.BALANCE_ADJUSTMENT, conflict.kind)

        assertTrue(repository.insertBalanceAdjustmentRecord(original.copy(operationId = "adjustment-command-2")).inserted)
        repository.softDeleteCurrentBalanceAdjustmentRecord(first.recordId, deletedAt = 500)
        val tombstoneReplay = repository.insertBalanceAdjustmentRecord(original)
        assertFalse(tombstoneReplay.inserted)
        assertEquals(500, repository.queryBalanceAdjustmentRecordByOperationId(original.operationId)?.deletedAt)
    }

    @Test
    fun `active edited rows cannot redefine the original operation payload`() = runBlocking {
        val repository = InMemoryTransactionRepository()

        val cashId = repository.insertCashFlowRecord(cash("edited-cash")).recordId
        val cash = requireNotNull(repository.queryCashFlowRecordById(cashId))
            .copy(amount = 20_000, updatedAt = 200)
        assertTrue(repository.updateCashFlowRecord(cash, expectedUpdatedAt = 100))
        assertEquals(
            LedgerRecordKind.CASH_FLOW,
            assertFailsWith<LedgerOperationConflictException> {
                repository.insertCashFlowRecord(cash.copy(id = 0))
            }.kind,
        )

        val transferId = repository.insertTransferRecord(transfer("edited-transfer")).recordId
        val transfer = requireNotNull(repository.queryTransferRecordById(transferId))
            .copy(amount = 3_000, updatedAt = 200)
        assertTrue(repository.updateTransferRecord(transfer, expectedUpdatedAt = 100))
        assertEquals(
            LedgerRecordKind.TRANSFER,
            assertFailsWith<LedgerOperationConflictException> {
                repository.insertTransferRecord(transfer.copy(id = 0))
            }.kind,
        )

        val updateId = repository.insertBalanceUpdateRecord(balanceUpdate("edited-update")).recordId
        val update = requireNotNull(repository.getBalanceUpdateRecordById(updateId))
            .copy(actualBalance = 13_000, delta = 3_000, updatedAt = 200)
        assertTrue(repository.updateBalanceUpdateRecord(update, expectedUpdatedAt = 100))
        assertEquals(
            LedgerRecordKind.BALANCE_UPDATE,
            assertFailsWith<LedgerOperationConflictException> {
                repository.insertBalanceUpdateRecord(update.copy(id = 0))
            }.kind,
        )

        val adjustmentId = repository.insertBalanceAdjustmentRecord(adjustment("edited-adjustment")).recordId
        val adjustment = requireNotNull(repository.getBalanceAdjustmentRecordById(adjustmentId))
            .copy(delta = -600, updatedAt = 200)
        assertTrue(repository.updateBalanceAdjustmentRecord(adjustment, expectedUpdatedAt = 100))
        assertEquals(
            LedgerRecordKind.BALANCE_ADJUSTMENT,
            assertFailsWith<LedgerOperationConflictException> {
                repository.insertBalanceAdjustmentRecord(adjustment.copy(id = 0))
            }.kind,
        )
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
    fun `explicit id rejection leaves in-memory ledger unchanged`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val first = repository.insertCashFlowRecord(cash("first"))

        val failure = assertFailsWith<IllegalArgumentException> {
            repository.insertCashFlowRecord(cash("second").copy(id = first.recordId))
        }

        assertEquals("新建账本记录的 ID 必须为 0", failure.message)
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

    private fun transactionRepositoryThatMustNotTouchRoom(): TransactionRepositoryImpl {
        return TransactionRepositoryImpl(
            database = UntouchedRoomDatabase(),
            cashFlowRecordDao = untouchedDao(),
            transferRecordDao = untouchedDao(),
            balanceUpdateRecordDao = untouchedDao(),
            balanceAdjustmentRecordDao = untouchedDao(),
            historyRecordDao = untouchedDao(),
        )
    }

    private inline fun <reified T : Any> untouchedDao(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, _ ->
            error("不应访问 ${method.declaringClass.simpleName}.${method.name}")
        } as T
    }

    private class UntouchedRoomDatabase : RoomDatabase() {
        override fun clearAllTables() {
            error("不应访问数据库")
        }

        override fun createInvalidationTracker(): InvalidationTracker {
            error("不应访问数据库")
        }
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
