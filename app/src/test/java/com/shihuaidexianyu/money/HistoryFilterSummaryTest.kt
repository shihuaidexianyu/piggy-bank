package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HistoryFilterSummaryTest {
    private val accountA = 1L
    private val accountB = 2L

    /**
     * Fixture: salary +2000 on A, lunch -500 on A, transfer 300 A→B, reconciliation +80 on B
     * (an investment-style gain), manual adjustment -30 on A. All at distinct times 100..500.
     */
    private fun seeded(): InMemoryTransactionRepository = runBlocking {
        val ledger = InMemoryTransactionRepository()
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountA, direction = CashFlowDirection.INFLOW.value, amount = 2_000L,
                note = "工资", occurredAt = 100L, createdAt = 100L, updatedAt = 100L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountA, direction = CashFlowDirection.OUTFLOW.value, amount = 500L,
                note = "午饭", occurredAt = 200L, createdAt = 200L, updatedAt = 200L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertTransferRecord(
            TransferRecord(
                fromAccountId = accountA, toAccountId = accountB, amount = 300L,
                note = "转入基金", occurredAt = 300L, createdAt = 300L, updatedAt = 300L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountB, actualBalance = 380L, systemBalanceBeforeUpdate = 300L,
                delta = 80L, occurredAt = 400L, createdAt = 400L, updatedAt = 400L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountA, delta = -30L, occurredAt = 500L, createdAt = 500L,
                updatedAt = 500L, operationId = testOperationId(),
            ),
        )
        ledger
    }

    @Test
    fun `global scope counts cash and deltas but not transfers`() = runBlocking {
        val summary = seeded().queryHistoryFilterSummary(HistoryRecordFilters())

        assertEquals(2_000L, summary.cashInflow)
        assertEquals(500L, summary.cashOutflow)
        // 2000 - 500 + 80 - 30; the internal transfer moves nothing in or out of the whole ledger.
        assertEquals(1_550L, summary.netChange)
    }

    @Test
    fun `account scope includes the signed transfer leg`() = runBlocking {
        val ledger = seeded()

        val a = ledger.queryHistoryFilterSummary(HistoryRecordFilters(accountId = accountA))
        // A: +2000 - 500 - 300 (transfer out) - 30 (adjustment).
        assertEquals(2_000L, a.cashInflow)
        assertEquals(500L, a.cashOutflow)
        assertEquals(1_170L, a.netChange)

        val b = ledger.queryHistoryFilterSummary(HistoryRecordFilters(accountId = accountB))
        // B: +300 (transfer in) + 80 (reconciliation gain).
        assertEquals(0L, b.cashInflow)
        assertEquals(0L, b.cashOutflow)
        assertEquals(380L, b.netChange)
    }

    @Test
    fun `date range limits the totals`() = runBlocking {
        val summary = seeded().queryHistoryFilterSummary(
            HistoryRecordFilters(dateStartAt = 150L, dateEndAt = 450L),
        )

        // Window covers: -500 lunch, transfer (neutral), +80 reconciliation.
        assertEquals(0L, summary.cashInflow)
        assertEquals(500L, summary.cashOutflow)
        assertEquals(-420L, summary.netChange)
    }

    @Test
    fun `type filter narrows what contributes`() = runBlocking {
        val summary = seeded().queryHistoryFilterSummary(
            HistoryRecordFilters(recordTypes = setOf(HistoryRecordType.CASH_FLOW)),
        )

        assertEquals(2_000L, summary.cashInflow)
        assertEquals(500L, summary.cashOutflow)
        assertEquals(1_500L, summary.netChange)
    }

    @Test
    fun `empty match yields zeros`() = runBlocking {
        val summary = seeded().queryHistoryFilterSummary(
            HistoryRecordFilters(keyword = "不存在的关键字"),
        )

        assertEquals(0L, summary.cashInflow)
        assertEquals(0L, summary.cashOutflow)
        assertEquals(0L, summary.netChange)
    }
}
