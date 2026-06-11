package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BalanceUpdateSameTimestampTest {
    @Test
    fun `same timestamp balance updates contribute fixed reconciliation deltas`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 10_000, createdAt = 1L),
        )
        val timestamp = 1_000L

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 1_000,
                purpose = "同毫秒支出",
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 9_200,
                systemBalanceBeforeUpdate = 9_000,
                delta = 200,
                occurredAt = timestamp,
                createdAt = timestamp,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 500,
                purpose = "同毫秒入账",
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 8_800,
                systemBalanceBeforeUpdate = 9_200,
                delta = -400,
                occurredAt = timestamp,
                createdAt = timestamp,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 300,
                purpose = "后续入账",
                occurredAt = timestamp + 1,
                createdAt = timestamp + 1,
                updatedAt = timestamp + 1,
            ),
        )

        val single = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val batch = CalculateAccountBalancesUseCase(transactionRepository)
        val account = requireNotNull(accountRepository.getAccountById(accountId))

        assertEquals(9_600, single(accountId))
        assertEquals(mapOf(accountId to 9_600L), batch(listOf(account)))
    }

    @Test
    fun `multiple ordinary records at same timestamp are all included`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 10_000, createdAt = 1L),
        )
        val timestamp = 1_000L

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                purpose = "入账一",
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 200,
                purpose = "入账二",
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 50,
                purpose = "支出",
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(10_250, useCase(accountId))
    }
}
