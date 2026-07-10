package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class AccountLifecycleRepositoryTest {
    @Test
    fun partitionsAndLifecycleMutations_preserveOrderBalanceAndIndependentHiddenState() = runBlocking {
        val repository = InMemoryAccountRepository()
        val firstId = repository.createAccount(account(name = "现金", balance = 100, order = 2, createdAt = 20))
        val hiddenId = repository.createAccount(
            account(name = "备用金", balance = -50, order = 1, createdAt = 10, hidden = true),
        )
        val closedId = repository.createAccount(account(name = "旧卡", balance = 300, order = 0, createdAt = 5))
        repository.closeAccount(closedId, closedAt = 99)

        assertEquals(listOf(closedId, hiddenId, firstId), repository.queryAllAccounts().map { it.id })
        assertEquals(listOf(hiddenId, firstId), repository.queryOpenAccounts().map { it.id })
        assertEquals(listOf(firstId), repository.queryVisibleOpenAccounts().map { it.id })
        assertEquals(listOf(hiddenId), repository.queryHiddenOpenAccounts().map { it.id })
        assertEquals(listOf(closedId), repository.queryClosedAccounts().map { it.id })
        assertEquals(repository.queryAllAccounts(), repository.observeAllAccounts().first())
        assertEquals(repository.queryOpenAccounts(), repository.observeOpenAccounts().first())
        assertEquals(repository.queryVisibleOpenAccounts(), repository.observeVisibleOpenAccounts().first())
        assertEquals(repository.queryHiddenOpenAccounts(), repository.observeHiddenOpenAccounts().first())
        assertEquals(repository.queryClosedAccounts(), repository.observeClosedAccounts().first())

        repository.setHidden(firstId, hidden = true)
        repository.setHidden(firstId, hidden = true)
        assertEquals(listOf(hiddenId, firstId), repository.queryHiddenOpenAccounts().map { it.id })
        assertEquals(100L, repository.getAccountById(firstId)?.initialBalance)
        assertEquals(2, repository.getAccountById(firstId)?.displayOrder)

        repository.setHidden(firstId, hidden = false)
        repository.setHidden(firstId, hidden = false)
        assertEquals(listOf(firstId), repository.queryVisibleOpenAccounts().map { it.id })

        repository.reopenAccount(closedId)
        assertEquals(null, repository.getAccountById(closedId)?.closedAt)
        assertEquals(300L, repository.getAccountById(closedId)?.initialBalance)
        assertEquals(listOf(closedId, hiddenId, firstId), repository.queryOpenAccounts().map { it.id })
    }

    private fun account(
        name: String,
        balance: Long,
        order: Int,
        createdAt: Long,
        hidden: Boolean = false,
    ) = Account(
        name = name,
        initialBalance = balance,
        createdAt = createdAt,
        isHidden = hidden,
        lastUsedAt = createdAt,
        displayOrder = order,
    )
}
