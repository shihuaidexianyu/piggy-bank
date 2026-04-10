package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DeleteCashFlowBalanceTest {
    @Test
    fun `deleting outflow recalculates balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(name = "现金", groupType = "payment", initialBalance = 10_000, createdAt = 1_000),
        )

        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val createCashFlow = CreateCashFlowRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        createCashFlow(
            accountId = accountId,
            direction = CashFlowDirection.INFLOW,
            amount = 2_000,
            purpose = "红包",
            occurredAt = 2_000,
        )
        val outflowId = createCashFlow(
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 500,
            purpose = "午饭",
            occurredAt = 3_000,
        )

        val calculate = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        assertEquals(11_500, calculate(accountId))
        assertEquals(3_000, accountRepository.getAccountById(accountId)?.lastUsedAt)

        val delete = DeleteCashFlowRecordUseCase(
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        delete(outflowId)
        delete(outflowId)

        assertEquals(12_000, calculate(accountId))
        assertEquals(2_000, accountRepository.getAccountById(accountId)?.lastUsedAt)
        assertNull(transactionRepository.queryCashFlowRecordById(outflowId))
    }
}
