package com.shihuaidexianyu.money

import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerOverflowException
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveSavingsGoalUseCaseTest {
    @Test
    fun `null goal avoids aggregate read`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val aggregate = CountingLedgerAggregateRepository(ledger)
        val useCase = ObserveSavingsGoalUseCase(
            accounts,
            InMemorySavingsGoalRepository(),
            ledger,
            CalculateAccountBalancesUseCase(aggregate, testClockProvider(1L)),
        )

        assertNull(useCase().first())
        assertEquals(0, aggregate.beforeCalls)
    }

    @Test
    fun `progress includes visible hidden and closed accounts and reacts to closed history`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val goals = InMemorySavingsGoalRepository()
        val visibleId = accounts.createAccount(Account(name = "显示", initialBalance = 100L, createdAt = 0L))
        val hiddenId = accounts.createAccount(Account(name = "隐藏", initialBalance = 200L, createdAt = 0L))
        val closedId = accounts.createAccount(Account(name = "关闭", initialBalance = 300L, createdAt = 0L))
        accounts.setHidden(hiddenId, true)
        accounts.closeAccount(closedId, closedAt = 10L)
        goals.upsert(targetAmount = 700L, now = 1L)
        val aggregate = CountingLedgerAggregateRepository(ledger)
        val useCase = ObserveSavingsGoalUseCase(
            accounts,
            goals,
            ledger,
            CalculateAccountBalancesUseCase(aggregate, testClockProvider(100L)),
        )

        useCase().test {
            val initial = awaitItem()
            assertEquals(600L, initial?.currentAmount)
            assertFalse(requireNotNull(initial).isAchieved)

            ledger.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = closedId,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 50L,
                    note = "关闭后迁移历史",
                    occurredAt = 20L,
                    createdAt = 20L,
                    updatedAt = 20L,
                    operationId = "closed-history",
                ),
            )
            assertEquals(650L, awaitItem()?.currentAmount)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(visibleId, 1L)
    }

    @Test
    fun `progress preserves a negative net worth`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        accounts.createAccount(Account(name = "负债", initialBalance = -500L, createdAt = 0L))
        val ledger = InMemoryTransactionRepository()
        val goals = InMemorySavingsGoalRepository().also { it.upsert(1_000L, 1L) }
        val useCase = ObserveSavingsGoalUseCase(
            accounts,
            goals,
            ledger,
            CalculateAccountBalancesUseCase(ledger, testClockProvider(1L)),
        )

        val progress = requireNotNull(useCase().first())
        assertEquals(-500L, progress.currentAmount)
        assertFalse(progress.isAchieved)
    }

    @Test
    fun `progress cross account overflow uses ledger domain failure`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        accounts.createAccount(Account(name = "最大", initialBalance = Long.MAX_VALUE, createdAt = 0L))
        accounts.createAccount(Account(name = "一", initialBalance = 1L, createdAt = 0L))
        val ledger = InMemoryTransactionRepository()
        val goals = InMemorySavingsGoalRepository().also { it.upsert(1L, 1L) }
        val useCase = ObserveSavingsGoalUseCase(
            accounts,
            goals,
            ledger,
            CalculateAccountBalancesUseCase(ledger, testClockProvider(1L)),
        )

        assertFailsWith<LedgerOverflowException> { useCase().first() }
        Unit
    }

    @Test
    fun `upsert use case samples clock exactly once`() = runBlocking {
        val repository = InMemorySavingsGoalRepository()
        val clock = CountingClockProvider(123L)
        val useCase = UpsertSavingsGoalUseCase(repository, clock)

        useCase(500L)

        assertEquals(1, clock.calls)
        assertEquals(123L, repository.query()?.createdAt)
    }

    @Test
    fun `upsert use case rejects zero and negative target before reading clock`() = runBlocking {
        val repository = InMemorySavingsGoalRepository()
        val clock = CountingClockProvider(123L)
        val useCase = UpsertSavingsGoalUseCase(repository, clock)

        assertFailsWith<IllegalArgumentException> { useCase(0L) }
        assertFailsWith<IllegalArgumentException> { useCase(-1L) }
        assertEquals(0, clock.calls)
        assertNull(repository.query())
    }

    private class CountingClockProvider(private val now: Long) : ClockProvider {
        var calls: Int = 0
            private set

        override fun nowMillis(): Long {
            calls += 1
            return now
        }
    }
}
