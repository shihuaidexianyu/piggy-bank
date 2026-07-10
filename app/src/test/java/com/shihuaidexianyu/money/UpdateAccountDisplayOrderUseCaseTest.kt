package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountDisplayOrderUseCaseTest {
    @Test
    fun `reorder rejects an account closed immediately before the transaction block`() = runBlocking {
        val delegate = InMemoryAccountRepository()
        val firstId = delegate.createAccount(
            Account(name = "A", initialBalance = 0L, createdAt = 1L, displayOrder = 0),
        )
        val secondId = delegate.createAccount(
            Account(name = "B", initialBalance = 0L, createdAt = 1L, displayOrder = 1),
        )
        val closeBeforeBlockRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                delegate.closeAccount(secondId, 5_000L)
                return block()
            }
        }

        assertFailsWith<IllegalArgumentException> {
            UpdateAccountDisplayOrderUseCase(delegate, closeBeforeBlockRunner)(listOf(secondId, firstId))
        }

        assertEquals(0, delegate.getAccountById(firstId)?.displayOrder)
        assertEquals(1, delegate.getAccountById(secondId)?.displayOrder)
        assertEquals(5_000L, delegate.getAccountById(secondId)?.closedAt)
    }

    @Test
    fun `reorders all open accounts including hidden by provided ids`() = runBlocking {
        val repository = InMemoryAccountRepository()
        val firstId = repository.createAccount(
            Account(name = "A", initialBalance = 0, createdAt = 1, displayOrder = 0),
        )
        val secondId = repository.createAccount(
            Account(name = "B", initialBalance = 0, createdAt = 1, isHidden = true, displayOrder = 1),
        )
        val thirdId = repository.createAccount(
            Account(name = "C", initialBalance = 0, createdAt = 1, displayOrder = 2),
        )

        UpdateAccountDisplayOrderUseCase(repository, directTransactionRunner)(
            orderedAccountIds = listOf(thirdId, firstId, secondId),
        )

        assertEquals(1, repository.getAccountById(firstId)?.displayOrder)
        assertEquals(2, repository.getAccountById(secondId)?.displayOrder)
        assertEquals(0, repository.getAccountById(thirdId)?.displayOrder)
    }

    @Test
    fun `rejects incomplete account ids to avoid inconsistent display orders`() = runBlocking {
        val repository = InMemoryAccountRepository()
        val firstId = repository.createAccount(
            Account(name = "A", initialBalance = 0, createdAt = 1, displayOrder = 0),
        )
        repository.createAccount(
            Account(name = "B", initialBalance = 0, createdAt = 1, displayOrder = 1),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            UpdateAccountDisplayOrderUseCase(repository, directTransactionRunner)(
                orderedAccountIds = listOf(firstId),
            )
        }

        assertEquals("账户顺序必须覆盖全部开放账户", error.message)
    }

    private val directTransactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
