package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.escapeHistoryLikeLiteral
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

class HistoryPreciseFilterTest {
    @Test
    fun `primary search covers notes account names and localized system titles literally`() = runBlocking {
        val names = mapOf(1L to "现金钱包", 2L to "工资卡")
        val repository = InMemoryTransactionRepository(accountNameLookup = names::get)
        insertCash(repository, 1L, CashFlowDirection.OUTFLOW, 100L, "午餐 100%_", 1_000L)
        insertCash(repository, 2L, CashFlowDirection.INFLOW, 200L, "100AB", 1_100L)
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 2L,
                actualBalance = 0L,
                systemBalanceBeforeUpdate = 0L,
                delta = 0L,
                occurredAt = 1_200L,
                createdAt = 1_200L,
                updatedAt = 1_200L,
                operationId = testOperationId(),
            ),
        )

        assertEquals(
            listOf("午餐 100%_"),
            repository.queryHistoryRecords(HistoryRecordFilters(keyword = "午餐"), null, 20).map { it.title },
        )
        assertEquals(
            setOf(HistoryRecordType.CASH_FLOW, HistoryRecordType.BALANCE_UPDATE),
            repository.queryHistoryRecords(HistoryRecordFilters(keyword = "工资卡"), null, 20).map { it.type }.toSet(),
        )
        assertEquals(
            listOf(HistoryRecordType.BALANCE_UPDATE),
            repository.queryHistoryRecords(HistoryRecordFilters(keyword = "余额核对"), null, 20).map { it.type },
        )
        assertEquals(
            listOf("午餐 100%_"),
            repository.queryHistoryRecords(HistoryRecordFilters(keyword = "100%_"), null, 20).map { it.title },
        )
        assertEquals("\\%\\_\\\\", escapeHistoryLikeLiteral("%_\\"))
    }

    @Test
    fun `advanced filters combine types exclude account half-open date amount and direction`() = runBlocking {
        val repository = InMemoryTransactionRepository(accountNameLookup = { id: Long -> "账户$id" })
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 200L, "保留", 1_000L)
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 250L, "排除咖啡", 1_100L)
        insertCash(repository, 1L, CashFlowDirection.OUTFLOW, 300L, "方向不符", 1_200L)
        insertCash(repository, 1L, CashFlowDirection.INFLOW, 400L, "终点", 2_000L)
        repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1L,
                actualBalance = 300L,
                systemBalanceBeforeUpdate = 0L,
                delta = 300L,
                occurredAt = 1_300L,
                createdAt = 1_300L,
                updatedAt = 1_300L,
                operationId = testOperationId(),
            ),
        )
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = 350L,
                occurredAt = 1_400L,
                createdAt = 1_400L,
                updatedAt = 1_400L,
                operationId = testOperationId(),
            ),
        )
        repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1L,
                toAccountId = 2L,
                amount = 150L,
                note = "转账",
                occurredAt = 1_500L,
                createdAt = 1_500L,
                updatedAt = 1_500L,
                operationId = testOperationId(),
            ),
        )

        val filters = HistoryRecordFilters(
            excludeKeyword = "咖啡",
            recordTypes = setOf(HistoryRecordType.CASH_FLOW, HistoryRecordType.BALANCE_UPDATE),
            accountId = 1L,
            dateStartAt = 1_000L,
            dateEndAt = 2_000L,
            minAmount = 100L,
            maxAmount = 300L,
            amountDirection = HistoryAmountDirection.INCREASE,
        )
        val records = repository.queryHistoryRecords(filters, cursor = null, limit = 20)

        assertEquals(
            listOf(HistoryRecordType.BALANCE_UPDATE, HistoryRecordType.CASH_FLOW),
            records.map { it.type },
        )
        assertEquals(2, repository.countHistoryRecords(filters))
    }

    @Test
    fun `amount magnitude filters handle Long MIN without overflow`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1L,
                delta = Long.MIN_VALUE,
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        )

        assertEquals(
            1,
            repository.countHistoryRecords(HistoryRecordFilters(minAmount = Long.MAX_VALUE)),
        )
        assertEquals(
            0,
            repository.countHistoryRecords(HistoryRecordFilters(maxAmount = Long.MAX_VALUE)),
        )
    }

    private suspend fun insertCash(
        repository: InMemoryTransactionRepository,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
    ) {
        repository.insertCashFlowRecord(
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
        )
    }
}
