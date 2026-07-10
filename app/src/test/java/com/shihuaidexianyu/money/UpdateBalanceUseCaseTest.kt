package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
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
            Account(
                name = "银行卡",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
            clockProvider = testClockProvider,
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 10_000,
            occurredAt = System.currentTimeMillis() - 1_000,
            operationId = testOperationId(),
        )

        assertEquals(0, result.delta)
        assertEquals(1, transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).size)
        assertTrue(transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).isEmpty())
    }

    @Test
    fun `reconciliation records delta without creating adjustment`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "证券账户",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
            clockProvider = testClockProvider,
        )

        val result = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 130_000,
            occurredAt = System.currentTimeMillis() - 1_000,
            operationId = testOperationId(),
        )

        assertEquals(30_000, result.delta)
        assertEquals(0, transactionRepository.queryBalanceAdjustmentRecordsByAccountId(accountId).size)
        assertEquals(1, transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId).size)
    }
}
