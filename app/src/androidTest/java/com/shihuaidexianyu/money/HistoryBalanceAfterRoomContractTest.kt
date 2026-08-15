package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract test pinning the bank-statement running balances (`balanceBefore`/`balanceAfter` plus
 * the transfer counterparty pair) produced by the SQL window in
 * [com.shihuaidexianyu.money.data.dao.HistoryRecordDao] against the in-memory repository fold:
 * same ledger in both implementations must yield identical per-row balance pairs, accumulated
 * over the COMPLETE ledger (filters, pagination, and tombstones must not move them).
 */
@RunWith(AndroidJUnit4::class)
class HistoryBalanceAfterRoomContractTest {
    private lateinit var db: MoneyDatabase
    private lateinit var roomRepo: TransactionRepositoryImpl
    private lateinit var memoryRepo: InMemoryTransactionRepository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomRepo = TransactionRepositoryImpl(
            database = db,
            cashFlowRecordDao = db.cashFlowRecordDao(),
            transferRecordDao = db.transferRecordDao(),
            balanceUpdateRecordDao = db.balanceUpdateRecordDao(),
            balanceAdjustmentRecordDao = db.balanceAdjustmentRecordDao(),
            historyRecordDao = db.historyRecordDao(),
            ledgerAggregateDao = db.ledgerAggregateDao(),
        )
        memoryRepo = InMemoryTransactionRepository(
            accountNameLookup = { id -> if (id == 1L) "现金钱包" else "储蓄卡" },
            accountInitialBalanceLookup = { id -> if (id == 1L) 1_000L else 0L },
        )
        db.accountDao().insert(
            AccountEntity(id = 1, name = "现金钱包", initialBalance = 1_000, createdAt = 1, displayOrder = 0),
        )
        db.accountDao().insert(
            AccountEntity(id = 2, name = "储蓄卡", initialBalance = 0, createdAt = 1, displayOrder = 1),
        )
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun runningBalanceMatchesAcrossImplementations() = runBlocking {
        forBoth { repo, tag ->
            repo.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 500L, "工资", 1_000L, "$tag-c1"))
            repo.insertCashFlowRecord(cash(1L, CashFlowDirection.OUTFLOW, 200L, "午餐", 1_100L, "$tag-c2"))
            repo.insertTransferRecord(transfer(1L, 2L, 300L, 1_200L, "$tag-t1"))
            repo.insertCashFlowRecord(cash(2L, CashFlowDirection.OUTFLOW, 50L, "购物", 1_300L, "$tag-c3"))
            repo.insertBalanceUpdateRecord(balanceUpdate(1L, 100L, 1_400L, "$tag-u1"))
            // Zero-delta checks stay stored but never appear in history; they contribute nothing.
            repo.insertBalanceUpdateRecord(balanceUpdate(1L, 0L, 1_450L, "$tag-u0"))
            repo.insertBalanceAdjustmentRecord(balanceAdjustment(1L, -100L, 1_500L, "$tag-a1"))
        }

        val roomRows = roomRepo.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)
        val memoryRows = memoryRepo.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)

        assertEquals(memoryRows.map(::shapeOf), roomRows.map(::shapeOf))
        // Newest first. Account 1 starts at 1_000: +500, -200, -300 (transfer out), +100, -100.
        // Account 2 starts at 0 and must include the incoming transfer leg: +300, -50.
        // Each row asserts the (before → after) pair of the row's own account.
        assertEquals(
            listOf(
                1_100L to 1_000L, // adjustment on account 1
                1_000L to 1_100L, // balance update on account 1
                300L to 250L, // account 2 expense after receiving the transfer
                1_300L to 1_000L, // transfer row reports the FROM account
                1_500L to 1_300L, // account 1 expense
                1_000L to 1_500L, // account 1 income
            ),
            roomRows.map { it.balanceBefore to it.balanceAfter },
        )
        // The transfer row resolves both account names from the accounts table and carries the
        // receiving account's before → after pair via the second legs join.
        val transferRow = roomRows.single { it.type == HistoryRecordType.TRANSFER }
        assertEquals("现金钱包", transferRow.accountName)
        assertEquals("储蓄卡", transferRow.relatedAccountName)
        assertEquals(0L, transferRow.relatedBalanceBefore)
        assertEquals(300L, transferRow.relatedBalanceAfter)
        // Non-transfer rows never carry a counterparty pair.
        roomRows.filter { it.type != HistoryRecordType.TRANSFER }.forEach { row ->
            assertEquals(null, row.relatedBalanceBefore)
            assertEquals(null, row.relatedBalanceAfter)
        }
        assertEquals("现金钱包", roomRows.first().accountName)
    }

    @Test
    fun filtersAndSoftDeletesNeverMoveTheRunningBalance() = runBlocking {
        forBoth { repo, tag ->
            repo.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 500L, "工资", 1_000L, "$tag-c1"))
            repo.insertTransferRecord(transfer(1L, 2L, 300L, 1_100L, "$tag-t1"))
            val deleted = repo.insertCashFlowRecord(
                cash(1L, CashFlowDirection.INFLOW, 999L, "误记", 1_150L, "$tag-c9"),
            ).recordId
            repo.softDeleteCurrentCashFlowRecord(deleted, 1_160L)
            repo.insertCashFlowRecord(cash(2L, CashFlowDirection.OUTFLOW, 50L, "购物", 1_200L, "$tag-c2"))
        }

        val roomUnfiltered = roomRepo.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)
        val memoryUnfiltered = memoryRepo.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)
        assertEquals(memoryUnfiltered.map(::shapeOf), roomUnfiltered.map(::shapeOf))

        // The tombstoned +999 is invisible to the SQL window as well.
        val byKeyword = roomRepo.queryHistoryRecords(HistoryRecordFilters(keyword = "购物"), cursor = null, limit = 20)
        assertEquals(listOf(300L to 250L), byKeyword.map { it.balanceBefore to it.balanceAfter })
        // Account filter matches the transfer through its receiving leg; pairs stay ledger-true.
        val byAccount = roomRepo.queryHistoryRecords(HistoryRecordFilters(accountId = 2L), cursor = null, limit = 20)
        assertEquals(
            listOf(300L to 250L, 1_500L to 1_200L),
            byAccount.map { it.balanceBefore to it.balanceAfter },
        )
        // The filtered-in transfer row still reports the receiving account's pair (0 → 300).
        assertEquals(
            listOf(null to null, 0L to 300L),
            byAccount.map { it.relatedBalanceBefore to it.relatedBalanceAfter },
        )
        val byDate = roomRepo.queryHistoryRecords(
            HistoryRecordFilters(dateStartAt = 1_100L),
            cursor = null,
            limit = 20,
        )
        assertEquals(
            listOf(300L to 250L, 1_500L to 1_200L),
            byDate.map { it.balanceBefore to it.balanceAfter },
        )
    }

    private suspend fun forBoth(block: suspend (TransactionRepository, String) -> Unit) {
        block(roomRepo, "room")
        block(memoryRepo, "memory")
    }

    private fun shapeOf(record: HistoryRecord): List<Any?> = listOf(
        record.type,
        record.accountId,
        record.relatedAccountId,
        record.amount,
        record.occurredAt,
        record.accountName,
        record.relatedAccountName,
        record.balanceBefore,
        record.balanceAfter,
        record.relatedBalanceBefore,
        record.relatedBalanceAfter,
    )

    private fun cash(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
        operationId: String,
    ): CashFlowRecord = CashFlowRecord(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        note = note,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        occurredAt: Long,
        operationId: String,
    ): TransferRecord = TransferRecord(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
        note = "",
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun balanceUpdate(
        accountId: Long,
        delta: Long,
        occurredAt: Long,
        operationId: String,
    ): BalanceUpdateRecord = BalanceUpdateRecord(
        accountId = accountId,
        actualBalance = 0L,
        systemBalanceBeforeUpdate = 0L,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun balanceAdjustment(
        accountId: Long,
        delta: Long,
        occurredAt: Long,
        operationId: String,
    ): BalanceAdjustmentRecord = BalanceAdjustmentRecord(
        accountId = accountId,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )
}
