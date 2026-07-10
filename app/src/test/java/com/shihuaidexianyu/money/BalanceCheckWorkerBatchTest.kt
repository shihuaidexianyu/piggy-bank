package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.notification.calculateBalanceCheckBalances
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BalanceCheckWorkerBatchTest {
    @Test
    fun `worker balance list uses one aggregate read`() = runBlocking {
        val aggregate = CountingLedgerAggregateRepository(InMemoryTransactionRepository())
        val accounts = listOf(
            Account(id = 1L, name = "A", initialBalance = 1L, createdAt = 0L),
            Account(id = 2L, name = "B", initialBalance = 2L, createdAt = 0L),
        )

        val balances = calculateBalanceCheckBalances(
            accounts,
            CalculateAccountBalancesUseCase(aggregate, testClockProvider(1L)),
        )

        assertEquals(mapOf(1L to 1L, 2L to 2L), balances)
        assertEquals(1, aggregate.beforeCalls)
    }
}
