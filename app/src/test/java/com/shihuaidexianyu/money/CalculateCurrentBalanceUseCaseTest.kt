package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CalculateCurrentBalanceUseCaseTest {
    @Test
    fun `balance at Long MAX_VALUE includes a record at that exact instant`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "边界账户", initialBalance = 10_000, createdAt = 1_000),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 500,
                purpose = "最大时间戳",
                occurredAt = Long.MAX_VALUE,
                createdAt = Long.MAX_VALUE,
                updatedAt = Long.MAX_VALUE,
            ),
        )
        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(10_500, useCase(accountId, atTimeMillis = Long.MAX_VALUE))
    }

    @Test
    fun `current balance includes record at current time and excludes future record`() = runBlocking {
        val currentTime = 5_000L
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "边界账户",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 500,
                purpose = "当前时刻",
                occurredAt = currentTime,
                createdAt = currentTime,
                updatedAt = currentTime,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 9_000,
                purpose = "未来记录",
                occurredAt = currentTime + 1,
                createdAt = currentTime + 1,
                updatedAt = currentTime + 1,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            clockProvider = testClockProvider(currentTime),
        )

        assertEquals(10_500, useCase(accountId))
    }

    @Test
    fun `balance without update uses initial balance and records`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "活期",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 500,
                purpose = "午饭",
                occurredAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 99,
                toAccountId = accountId,
                amount = 300,
                note = "",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -100,
                occurredAt = 5_000,
                createdAt = 5_000,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(11_700, useCase(accountId))
    }

    @Test
    fun `balance with reconciliation update applies fixed delta`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "证券",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 10_000,
                purpose = "旧记录",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 120_000,
                systemBalanceBeforeUpdate = 110_000,
                delta = 10_000,
                occurredAt = 3_000,
                createdAt = 3_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 5_000,
                purpose = "更新后支出",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(115_000, useCase(accountId))
    }

    @Test
    fun `balance before account opening event is zero`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "活期",
                initialBalance = 10_000,
                createdAt = 120_000,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(0, useCase(accountId, atTimeMillis = 119_999))
        assertEquals(10_000, useCase(accountId, atTimeMillis = 120_000))
    }

    @Test
    fun `balance includes cash flow recorded in same minute as account creation`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "现金",
                initialBalance = 10_000,
                createdAt = 1712106635000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 2_000,
                purpose = "",
                occurredAt = 1712106600000,
                createdAt = 1712106636000,
                updatedAt = 1712106636000,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(8_000, useCase(accountId))
    }
}
