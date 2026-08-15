package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Covers the bank-statement running balances (`balanceBefore`/`balanceAfter`, plus the transfer
 * counterparty pair `relatedBalanceBefore`/`relatedBalanceAfter`) on history rows: values must be
 * the account's book balance right around that record, accumulated over the COMPLETE ledger —
 * never affected by active filters, pagination, or soft-deleted rows.
 */
class HistoryBalanceAfterTest {
    private fun newRepository(): InMemoryTransactionRepository = InMemoryTransactionRepository(
        accountNameLookup = { id ->
            when (id) {
                1L -> "现金钱包"
                2L -> "储蓄卡"
                else -> null
            }
        },
        accountInitialBalanceLookup = { id -> if (id == 1L) 1_000L else 0L },
    )

    @Test
    fun `balance pair accumulates all four record types and both transfer legs`() = runBlocking {
        val repository = newRepository()
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 500L, "工资", 1_000L)
        insertCash(repository, 1L, CashFlowDirection.OUTFLOW, 200L, "午餐", 1_100L)
        insertTransfer(repository, fromAccountId = 1L, toAccountId = 2L, amount = 300L, occurredAt = 1_200L)
        insertCash(repository, 2L, CashFlowDirection.OUTFLOW, 50L, "储蓄卡支出", 1_300L)
        repository.insertBalanceUpdateRecord(balanceUpdate(accountId = 1L, delta = 100L, occurredAt = 1_400L))
        repository.insertBalanceAdjustmentRecord(balanceAdjustment(accountId = 1L, delta = -100L, occurredAt = 1_500L))

        val records = repository.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)

        // Newest first. Account 1 starts at 1_000 (initial): +500, -200, -300 (transfer out),
        // +100 (reconciliation), -100 (manual adjustment). Account 2 starts at 0 and MUST see the
        // incoming transfer leg: +300, -50. Each row asserts the (before → after) pair.
        assertEquals(
            listOf(
                1_100L to 1_000L, // adjustment on account 1: 1_100 - 100
                1_000L to 1_100L, // balance update on account 1: 1_000 + 100
                300L to 250L, // account 2 expense AFTER receiving the transfer: 300 - 50
                1_300L to 1_000L, // transfer row shows the FROM account: 1_300 - 300
                1_500L to 1_300L, // account 1 expense: 1_500 - 200
                1_000L to 1_500L, // account 1 income: 1_000 + 500
            ),
            records.map { it.balanceBefore to it.balanceAfter },
        )
    }

    @Test
    fun `balance pairs are computed over the full ledger regardless of active filters`() = runBlocking {
        val repository = newRepository()
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 500L, "工资", 1_000L)
        insertTransfer(repository, fromAccountId = 1L, toAccountId = 2L, amount = 300L, occurredAt = 1_100L)
        insertCash(repository, 2L, CashFlowDirection.OUTFLOW, 50L, "购物", 1_200L)

        val unfiltered = repository.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)
        val byKeyword = repository.queryHistoryRecords(
            HistoryRecordFilters(keyword = "购物"),
            cursor = null,
            limit = 20,
        )
        val byAccount = repository.queryHistoryRecords(
            HistoryRecordFilters(accountId = 2L),
            cursor = null,
            limit = 20,
        )
        val byDate = repository.queryHistoryRecords(
            HistoryRecordFilters(dateStartAt = 1_100L),
            cursor = null,
            limit = 20,
        )

        assertEquals(listOf(300L to 250L), byKeyword.map { it.balanceBefore to it.balanceAfter })
        // The account filter also matches the transfer via its receiving leg; the row still
        // reports the FROM account pair (1_500 → 1_200) plus the receiving pair (0 → 300).
        assertEquals(
            listOf(300L to 250L, 1_500L to 1_200L),
            byAccount.map { it.balanceBefore to it.balanceAfter },
        )
        assertEquals(
            listOf(300L to 250L, 1_500L to 1_200L),
            byDate.map { it.balanceBefore to it.balanceAfter },
        )
        // Same rows, same balance pairs with and without filters. recordId is only unique per
        // ledger table, so key by (type, recordId).
        val unfilteredById = unfiltered.associate { row ->
            (row.type to row.recordId) to BalanceQuad(row)
        }
        (byKeyword + byAccount + byDate).forEach { record ->
            assertEquals(unfilteredById[record.type to record.recordId], BalanceQuad(record))
        }
    }

    @Test
    fun `soft deleted records do not contribute to later balance pairs`() = runBlocking {
        val repository = newRepository()
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 500L, "工资", 1_000L)
        val deletedId = insertCash(repository, 1L, CashFlowDirection.INFLOW, 999L, "误记", 1_100L)
        repository.softDeleteCurrentCashFlowRecord(deletedId, 1_150L)
        insertCash(repository, 1L, CashFlowDirection.OUTFLOW, 200L, "午餐", 1_200L)

        val records = repository.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)

        assertEquals(listOf("午餐", "工资"), records.map { it.title })
        // The tombstoned +999 is invisible to the running balance: 1_000 + 500 - 200.
        assertEquals(
            listOf(1_500L to 1_300L, 1_000L to 1_500L),
            records.map { it.balanceBefore to it.balanceAfter },
        )
    }

    @Test
    fun `same instant records accumulate in union source order`() = runBlocking {
        val repository = newRepository()
        // Same account, same occurredAt: adjustment (sourceOrder 1) accumulates before the cash
        // flow (sourceOrder 4), matching the SQL window's ORDER BY occurredAt, sourceOrder, recordId.
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 10L, "同刻入账", 2_000L)
        repository.insertBalanceAdjustmentRecord(balanceAdjustment(accountId = 1L, delta = 5L, occurredAt = 2_000L))

        val records = repository.queryHistoryRecords(HistoryRecordFilters(), cursor = null, limit = 20)

        assertEquals(
            listOf(HistoryRecordType.CASH_FLOW, HistoryRecordType.BALANCE_ADJUSTMENT),
            records.map { it.type },
        )
        // Display order is newest-key first: the cash flow sits on top and already includes the adjustment.
        assertEquals(
            listOf(1_005L to 1_015L, 1_000L to 1_005L),
            records.map { it.balanceBefore to it.balanceAfter },
        )
    }

    @Test
    fun `transfer row carries both accounts before and after balances`() = runBlocking {
        val repository = newRepository()
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 500L, "工资", 800L)
        insertCash(repository, 2L, CashFlowDirection.INFLOW, 70L, "原有", 900L)
        insertTransfer(repository, fromAccountId = 1L, toAccountId = 2L, amount = 300L, occurredAt = 1_000L)

        val transfer = repository.queryHistoryRecords(
            HistoryRecordFilters(recordTypes = setOf(HistoryRecordType.TRANSFER)),
            cursor = null,
            limit = 20,
        ).single()

        assertEquals("现金钱包", transfer.accountName)
        assertEquals("储蓄卡", transfer.relatedAccountName)
        // FROM account: 1_000 + 500 - 300; TO account: 0 + 70 + 300.
        assertEquals(1_500L, transfer.balanceBefore)
        assertEquals(1_200L, transfer.balanceAfter)
        assertEquals(70L, transfer.relatedBalanceBefore)
        assertEquals(370L, transfer.relatedBalanceAfter)
        // Non-transfer rows never carry a counterparty pair.
        val cashRows = repository.queryHistoryRecords(
            HistoryRecordFilters(recordTypes = setOf(HistoryRecordType.CASH_FLOW)),
            cursor = null,
            limit = 20,
        )
        cashRows.forEach { row ->
            assertEquals(null, row.relatedBalanceBefore)
            assertEquals(null, row.relatedBalanceAfter)
        }
    }

    @Test
    fun `self transfer degrades to an unchanged pair on both sides`() = runBlocking {
        val repository = newRepository()
        // Creation use cases reject self-transfers; the ledger fold must stay consistent if one
        // ever slips in: a single zero leg keeps before == after on both reported accounts.
        insertTransfer(repository, fromAccountId = 1L, toAccountId = 1L, amount = 300L, occurredAt = 900L)

        val transfer = repository.queryHistoryRecords(
            HistoryRecordFilters(recordTypes = setOf(HistoryRecordType.TRANSFER)),
            cursor = null,
            limit = 20,
        ).single()

        assertEquals(1_000L, transfer.balanceBefore)
        assertEquals(1_000L, transfer.balanceAfter)
        assertEquals(1_000L, transfer.relatedBalanceBefore)
        assertEquals(1_000L, transfer.relatedBalanceAfter)
    }

    /** All four balance fields of one history row, for pair-wise contract assertions. */
    private data class BalanceQuad(
        val balanceBefore: Long?,
        val balanceAfter: Long?,
        val relatedBalanceBefore: Long?,
        val relatedBalanceAfter: Long?,
    ) {
        constructor(record: HistoryRecord) : this(
            balanceBefore = record.balanceBefore,
            balanceAfter = record.balanceAfter,
            relatedBalanceBefore = record.relatedBalanceBefore,
            relatedBalanceAfter = record.relatedBalanceAfter,
        )
    }

    private suspend fun insertCash(
        repository: InMemoryTransactionRepository,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
    ): Long {
        return repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        ).recordId
    }

    private suspend fun insertTransfer(
        repository: InMemoryTransactionRepository,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        occurredAt: Long,
    ) {
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                note = "",
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
    }

    private fun balanceUpdate(accountId: Long, delta: Long, occurredAt: Long): BalanceUpdateRecord {
        return BalanceUpdateRecord(
            accountId = accountId,
            actualBalance = 0L,
            systemBalanceBeforeUpdate = 0L,
            delta = delta,
            occurredAt = occurredAt,
            createdAt = occurredAt,
            updatedAt = occurredAt,
            operationId = testOperationId(),
        )
    }

    private fun balanceAdjustment(accountId: Long, delta: Long, occurredAt: Long): BalanceAdjustmentRecord {
        return BalanceAdjustmentRecord(
            accountId = accountId,
            delta = delta,
            occurredAt = occurredAt,
            createdAt = occurredAt,
            updatedAt = occurredAt,
            operationId = testOperationId(),
        )
    }
}
