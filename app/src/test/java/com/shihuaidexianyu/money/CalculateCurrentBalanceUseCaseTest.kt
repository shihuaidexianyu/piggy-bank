package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CalculateCurrentBalanceUseCaseTest {
    @Test
    fun `balance without update uses initial balance and records`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "活期",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
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
            CashFlowRecordEntity(
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
            TransferRecordEntity(
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
            BalanceAdjustmentRecordEntity(
                accountId = accountId,
                delta = -100,
                sourceUpdateRecordId = 0,
                occurredAt = 5_000,
                createdAt = 5_000,
            ),
        )

        val useCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)

        assertEquals(11_700, useCase(accountId))
    }

    @Test
    fun `balance with latest update uses update as anchor`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "证券",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
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
            BalanceUpdateRecordEntity(
                accountId = accountId,
                actualBalance = 120_000,
                systemBalanceBeforeUpdate = 110_000,
                delta = 10_000,
                occurredAt = 3_000,
                createdAt = 3_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
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
    fun `balance includes cash flow recorded in same minute as account creation`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "现金",
                initialBalance = 10_000,
                createdAt = 1712106635000,
            ),
        )

        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
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

