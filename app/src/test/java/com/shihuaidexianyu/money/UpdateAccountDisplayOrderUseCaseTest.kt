package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountDisplayOrderUseCaseTest {
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

        UpdateAccountDisplayOrderUseCase(repository)(
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
            UpdateAccountDisplayOrderUseCase(repository)(
                orderedAccountIds = listOf(firstId),
            )
        }

        assertEquals("账户顺序必须覆盖全部开放账户", error.message)
    }
}
