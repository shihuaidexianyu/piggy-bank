package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HomeStatsSemanticsTest {
    @Test
    fun `stats split cash flow balance updates and manual adjustments`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val startAt = 1_000L
        val endAt = 10_000L

        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.INFLOW.value,
                amount = 2_000,
                note = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500,
                note = "午饭",
                occurredAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
                operationId = testOperationId(),
            ),
        )
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = 9_999,
                note = "转账不进入现金流合计",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 12_000,
                systemBalanceBeforeUpdate = 11_700,
                delta = 300,
                occurredAt = 5_000,
                createdAt = 5_000,
                updatedAt = 5_000,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 11_000,
                systemBalanceBeforeUpdate = 11_700,
                delta = -700,
                occurredAt = 6_000,
                createdAt = 6_000,
                updatedAt = 6_000,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 400,
                occurredAt = 7_000,
                createdAt = 7_000,
                updatedAt = 7_000,
                operationId = testOperationId(),
            ),
        )

        assertEquals(2_000L, repository.sumCashInflowBetween(startAt, endAt))
        assertEquals(500L, repository.sumCashOutflowBetween(startAt, endAt))
        assertEquals(300L, repository.sumBalanceUpdateIncreaseBetween(startAt, endAt))
        assertEquals(700L, repository.sumBalanceUpdateDecreaseBetween(startAt, endAt))
        assertEquals(400L, repository.sumManualAdjustmentIncreaseBetween(startAt, endAt))
        assertEquals(0L, repository.sumManualAdjustmentDecreaseBetween(startAt, endAt))
        assertEquals(2, repository.countActiveCashFlowRecordsBetween(startAt, endAt))
        assertEquals(1, repository.countActiveTransferRecordsBetween(startAt, endAt))
        assertEquals(1, repository.countManualAdjustmentRecordsBetween(startAt, endAt))
    }

    @Test
    fun `period queries include start exclude end and ignore deleted records`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val startAt = 1_000L
        val endAt = 10_000L
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                note = "边界外",
                occurredAt = startAt,
                createdAt = startAt,
                updatedAt = startAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.INFLOW.value,
                amount = 200,
                note = "边界内",
                occurredAt = endAt,
                createdAt = endAt,
                updatedAt = endAt,
                operationId = testOperationId(),
            ),
        )
        val deletedCashFlowId = repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 300,
                note = "已删除",
                occurredAt = 5_000,
                createdAt = 5_000,
                updatedAt = 5_000,
                operationId = testOperationId(),
            ),
        ).recordId
        repository.softDeleteCurrentCashFlowRecord(deletedCashFlowId, 5_001)
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = 400,
                note = "起点转账",
                occurredAt = startAt,
                createdAt = startAt,
                updatedAt = startAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = 500,
                note = "终点转账",
                occurredAt = endAt,
                createdAt = endAt,
                updatedAt = endAt,
                operationId = testOperationId(),
            ),
        )
        val deletedTransferId = repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 2L,
                toAccountId = 1L,
                amount = 550,
                note = "已删除",
                occurredAt = 5_000,
                createdAt = 5_000,
                updatedAt = 5_000,
                operationId = testOperationId(),
            ),
        ).recordId
        repository.softDeleteCurrentTransferRecord(deletedTransferId, 5_001)
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 600,
                occurredAt = startAt,
                createdAt = startAt,
                updatedAt = startAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 700,
                occurredAt = endAt,
                createdAt = endAt,
                updatedAt = endAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 800,
                systemBalanceBeforeUpdate = 100,
                delta = 700,
                occurredAt = startAt,
                createdAt = startAt,
                updatedAt = startAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 1_000,
                systemBalanceBeforeUpdate = 100,
                delta = 900,
                occurredAt = endAt,
                createdAt = endAt,
                updatedAt = endAt,
                operationId = testOperationId(),
            ),
        )

        assertEquals(100L, repository.sumCashInflowBetween(startAt, endAt))
        assertEquals(listOf(400L), repository.queryActiveTransferRecordsBetween(startAt, endAt).map { it.amount })
        assertEquals(600L, repository.sumManualAdjustmentIncreaseBetween(startAt, endAt))
        assertEquals(700L, repository.sumBalanceUpdateIncreaseBetween(startAt, endAt))
        assertEquals(1, repository.countActiveCashFlowRecordsBetween(startAt, endAt))
        assertEquals(1, repository.countActiveTransferRecordsBetween(startAt, endAt))
        assertEquals(1, repository.countManualAdjustmentRecordsBetween(startAt, endAt))
    }
}
