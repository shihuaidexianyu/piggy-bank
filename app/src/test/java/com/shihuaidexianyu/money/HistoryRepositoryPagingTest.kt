package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HistoryRepositoryPagingTest {
    @Test
    fun `same timestamp keyset has no duplicate or gap when next-page row is tombstoned`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val ids = (1L..6L).map { amount ->
            repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1L,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = amount,
                    note = "记录$amount",
                    occurredAt = 1_000L,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    operationId = testOperationId(),
                ),
            ).recordId
        }
        val first = repository.queryHistoryRecords(HistoryRecordFilters(), null, 2)
        repository.softDeleteCurrentCashFlowRecord(ids[3], 2_000L)
        val second = repository.queryHistoryRecords(HistoryRecordFilters(), first.last().cursor, 2)
        val third = repository.queryHistoryRecords(HistoryRecordFilters(), second.last().cursor, 2)

        val combined = first + second + third
        assertEquals(combined.map { it.recordId }.distinct(), combined.map { it.recordId })
        assertEquals(ids.filterNot { it == ids[3] }.toSet(), combined.map { it.recordId }.toSet())
    }

    @Test
    fun `history query pages across record types with stable cursor order`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val occurredAt = 1_000L
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                note = "工资",
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = 200,
                note = "转账",
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 300,
                systemBalanceBeforeUpdate = 100,
                delta = 200,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = -50,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )

        val firstPage = repository.queryHistoryRecords(
            filters = HistoryRecordFilters(),
            cursor = null,
            limit = 2,
        )
        val secondPage = repository.queryHistoryRecords(
            filters = HistoryRecordFilters(),
            cursor = firstPage.last().cursor,
            limit = 2,
        )

        assertEquals(listOf(HistoryRecordType.CASH_FLOW, HistoryRecordType.TRANSFER), firstPage.map { it.type })
        assertEquals(
            listOf(HistoryRecordType.BALANCE_UPDATE, HistoryRecordType.BALANCE_ADJUSTMENT),
            secondPage.map { it.type },
        )
        assertEquals(4, repository.countHistoryRecords(HistoryRecordFilters()))
    }

    @Test
    fun `history filters run before paging and exclude deleted records`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val deletedId = repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 100,
                note = "午餐 咖啡",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        repository.softDeleteCurrentCashFlowRecord(deletedId, 1_001L)
        repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200,
                note = "午餐 米饭",
                occurredAt = 2_000L,
                createdAt = 2_000L,
                updatedAt = 2_000L,
                operationId = testOperationId(),
            ),
        )
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 2L,
                toAccountId = 1L,
                amount = 300,
                note = "午餐垫付",
                occurredAt = 3_000L,
                createdAt = 3_000L,
                updatedAt = 3_000L,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 400,
                occurredAt = 4_000L,
                createdAt = 4_000L,
                updatedAt = 4_000L,
                operationId = testOperationId(),
            ),
        )

        val filters = HistoryRecordFilters(
            keyword = "午餐",
            excludeKeyword = "咖啡",
            accountId = 1L,
            minAmount = 150L,
            amountDirection = HistoryAmountDirection.ALL,
        )
        val records = repository.queryHistoryRecords(filters = filters, cursor = null, limit = 10)

        assertEquals(listOf(HistoryRecordType.TRANSFER, HistoryRecordType.CASH_FLOW), records.map { it.type })
        assertEquals(2, repository.countHistoryRecords(filters))
        assertEquals(
            listOf(HistoryRecordType.BALANCE_ADJUSTMENT),
            repository.queryHistoryRecords(
                filters = HistoryRecordFilters(amountDirection = HistoryAmountDirection.INCREASE),
                cursor = null,
                limit = 10,
            ).map { it.type },
        )
    }

    @Test
    fun `history date range includes start and excludes end`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        listOf(1_000L, 2_000L).forEach { occurredAt ->
            repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1L,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = occurredAt,
                    note = "边界记录",
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    operationId = testOperationId(),
                ),
            )
        }

        val filters = HistoryRecordFilters(dateStartAt = 1_000L, dateEndAt = 2_000L)
        val records = repository.queryHistoryRecords(filters = filters, cursor = null, limit = 10)

        assertEquals(listOf(1_000L), records.map { it.occurredAt })
        assertEquals(1, repository.countHistoryRecords(filters))
    }
}
