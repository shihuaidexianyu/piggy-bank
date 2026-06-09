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
                purpose = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500,
                purpose = "午饭",
                occurredAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
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
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 400,
                sourceUpdateRecordId = 0L,
                occurredAt = 7_000,
                createdAt = 7_000,
            ),
        )

        assertEquals(2_000L, repository.sumCashInflowBetween(startAt, endAt))
        assertEquals(500L, repository.sumCashOutflowBetween(startAt, endAt))
        assertEquals(300L, repository.sumBalanceUpdateIncreaseBetween(startAt, endAt))
        assertEquals(700L, repository.sumBalanceUpdateDecreaseBetween(startAt, endAt))
        assertEquals(400L, repository.sumManualAdjustmentIncreaseBetween(startAt, endAt))
        assertEquals(0L, repository.sumManualAdjustmentDecreaseBetween(startAt, endAt))
    }
}
