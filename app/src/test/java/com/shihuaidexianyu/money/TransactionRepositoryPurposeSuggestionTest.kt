package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TransactionRepositoryPurposeSuggestionTest {
    @Test
    fun `recent purposes are unique and account scoped`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        repository.insertCashFlowRecord(cashFlow(accountId = 1, purpose = "早餐", occurredAt = 1_000))
        repository.insertCashFlowRecord(cashFlow(accountId = 2, purpose = "咖啡", occurredAt = 2_000))
        repository.insertCashFlowRecord(cashFlow(accountId = 1, purpose = "早餐", occurredAt = 3_000))
        repository.insertCashFlowRecord(cashFlow(accountId = 1, purpose = "地铁", occurredAt = 4_000))

        val accountSuggestions = repository.queryRecentCashFlowPurposes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = 1,
            limit = 6,
        )
        val globalSuggestions = repository.queryRecentCashFlowPurposes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = null,
            limit = 2,
        )

        assertEquals(listOf("地铁", "早餐"), accountSuggestions)
        assertEquals(listOf("地铁", "早餐"), globalSuggestions)
    }

    private fun cashFlow(
        accountId: Long,
        purpose: String,
        occurredAt: Long,
    ): CashFlowRecord {
        return CashFlowRecord(
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 100,
            purpose = purpose,
            occurredAt = occurredAt,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )
    }
}
