package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerOverflowException
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LedgerAggregateRepositoryTest {
    @Test
    fun `mixed aggregate respects opening tombstones transfer sides fixed delta and requested keys`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val hidden = account(id = 1L, initialBalance = -100L, createdAt = 95_000L, hidden = true)
        val closed = account(id = 2L, initialBalance = 50L, createdAt = 120_035L, closedAt = 150_000L)
        val future = account(id = 3L, initialBalance = 500L, createdAt = 400_035L)

        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 200L, 60_000L, "in-opening"))
        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.OUTFLOW, 50L, 70_000L, "out"))
        repository.insertCashFlowRecord(
            cash(1L, CashFlowDirection.INFLOW, 999L, 80_000L, "deleted").copy(deletedAt = 81_000L),
        )
        repository.insertTransferRecord(transfer(1L, 2L, 40L, 120_000L, "transfer"))
        repository.insertBalanceAdjustmentRecord(adjustment(1L, -10L, 130_000L, "adjustment"))
        repository.insertBalanceUpdateRecord(
            balanceUpdate(
                accountId = 1L,
                actualBalance = 9_999L,
                systemBalanceBefore = -9_999L,
                delta = 5L,
                occurredAt = 140_000L,
                operationId = "fixed-delta",
            ),
        )
        repository.insertCashFlowRecord(cash(2L, CashFlowDirection.INFLOW, 7L, 200_000L, "after-close"))

        val aggregates = repository.queryBefore(
            accounts = listOf(hidden, closed, future),
            endExclusive = 300_000L,
        )

        assertEquals(
            AccountLedgerAggregate(
                accountId = 1L,
                inflow = 200L,
                outflow = 50L,
                transferOut = 40L,
                manualAdjustment = -10L,
                reconciliation = 5L,
            ),
            aggregates.getValue(1L),
        )
        assertEquals(
            AccountLedgerAggregate(accountId = 2L, inflow = 7L, transferIn = 40L),
            aggregates.getValue(2L),
        )
        assertEquals(AccountLedgerAggregate(accountId = 3L), aggregates.getValue(3L))
        assertEquals(setOf(1L, 2L, 3L), aggregates.keys)
    }

    @Test
    fun `before MAX excludes MAX event while at MAX includes it`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val account = account(id = 1L, initialBalance = 0L, createdAt = 0L)
        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 10L, Long.MAX_VALUE, "max"))

        assertEquals(0L, repository.queryBefore(listOf(account), Long.MAX_VALUE).getValue(1L).inflow)
        assertEquals(10L, repository.queryAt(listOf(account), Long.MAX_VALUE).getValue(1L).inflow)
    }

    @Test
    fun `excluded reconciliation id is omitted without using evidence fields`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val account = account(id = 1L, initialBalance = 0L, createdAt = 0L)
        val excludedId = repository.insertBalanceUpdateRecord(
            balanceUpdate(1L, 1_000L, -1_000L, 25L, 100L, "excluded"),
        ).recordId
        repository.insertBalanceUpdateRecord(
            balanceUpdate(1L, -5_000L, 5_000L, -7L, 100L, "included"),
        )

        val aggregate = repository.queryBefore(
            accounts = listOf(account),
            endExclusive = 101L,
            excludingBalanceUpdateId = excludedId,
        ).getValue(1L)

        assertEquals(-7L, aggregate.reconciliation)
    }

    @Test
    fun `activity maxima ignore tombstones and include both transfer sides`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 1L, 10L, "cash"))
        repository.insertTransferRecord(transfer(2L, 1L, 1L, 20L, "transfer-in"))
        repository.insertTransferRecord(transfer(1L, 2L, 1L, 30L, "transfer-out"))
        repository.insertBalanceUpdateRecord(balanceUpdate(1L, 0L, 0L, 0L, 25L, "balance"))
        repository.insertBalanceAdjustmentRecord(
            adjustment(1L, 1L, 40L, "deleted-latest").copy(deletedAt = 41L),
        )

        val maxima = repository.queryActivityMaxima(1L)

        assertEquals(30L, maxima.maxActiveOccurredAt)
        assertEquals(25L, maxima.maxBalanceUpdateOccurredAt)
    }

    @Test
    fun `component overflow uses shared domain failure`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val account = account(id = 1L, initialBalance = 0L, createdAt = 0L)
        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, Long.MAX_VALUE, 1L, "max"))
        repository.insertCashFlowRecord(cash(1L, CashFlowDirection.INFLOW, 1L, 2L, "overflow"))

        assertFailsWith<LedgerOverflowException> {
            repository.queryBefore(listOf(account), endExclusive = 3L)
        }
        Unit
    }

    @Test
    fun `legacy stats sums purpose and daily paths use shared domain failure`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        repository.insertCashFlowRecord(
            cash(1L, CashFlowDirection.INFLOW, Long.MAX_VALUE, 1L, "purpose-1").copy(note = "same-purpose"),
        )
        repository.insertCashFlowRecord(
            cash(1L, CashFlowDirection.INFLOW, 1L, 2L, "purpose-2").copy(note = "same-purpose"),
        )

        assertFailsWith<LedgerOverflowException> { repository.sumCashInflowBetween(0L, 3L) }
        assertFailsWith<LedgerOverflowException> {
            repository.queryPurposeTotals(CashFlowDirection.INFLOW.value, 0L, 3L)
        }
        assertFailsWith<LedgerOverflowException> { repository.queryDailyCashFlowTotals(0L, 3L, 0) }
        Unit
    }

    private fun account(
        id: Long,
        initialBalance: Long,
        createdAt: Long,
        hidden: Boolean = false,
        closedAt: Long? = null,
    ) = Account(
        id = id,
        name = "账户$id",
        initialBalance = initialBalance,
        createdAt = createdAt,
        isHidden = hidden,
        closedAt = closedAt,
    )

    private fun cash(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        occurredAt: Long,
        operationId: String,
    ) = CashFlowRecord(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        note = operationId,
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
    ) = TransferRecord(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
        note = operationId,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun adjustment(
        accountId: Long,
        delta: Long,
        occurredAt: Long,
        operationId: String,
    ) = BalanceAdjustmentRecord(
        accountId = accountId,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun balanceUpdate(
        accountId: Long,
        actualBalance: Long,
        systemBalanceBefore: Long,
        delta: Long,
        occurredAt: Long,
        operationId: String,
    ) = BalanceUpdateRecord(
        accountId = accountId,
        actualBalance = actualBalance,
        systemBalanceBeforeUpdate = systemBalanceBefore,
        delta = delta,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )
}
