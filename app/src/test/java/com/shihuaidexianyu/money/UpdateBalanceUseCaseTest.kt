package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateBalanceUseCaseTest {
    @Test
    fun `matching actual balance does not create adjustment`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = "bank",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 10_000,
            occurredAt = System.currentTimeMillis() - 1_000,
        )

        assertEquals(0, result.delta)
        assertEquals(1, transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).size)
        assertTrue(transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).isEmpty())
    }

    @Test
    fun `investment update still records delta without settlement data`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "证券账户",
                groupType = "investment",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 130_000,
            occurredAt = System.currentTimeMillis() - 1_000,
        )

        assertEquals(30_000, result.delta)
        assertEquals(0, transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).size)
        assertEquals(1, transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).size)
    }
}

