package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RecordTimeValidationTest {
    @Test
    fun `cash flow cannot be created before account start minute`() {
        runBlocking {
            val accountRepository = InMemoryAccountRepository()
            val transactionRepository = InMemoryTransactionRepository()
            val accountId = accountRepository.createAccount(
                Account(
                    name = "钱包",
                    initialBalance = 0,
                    createdAt = 61_000,
                ),
            )
            val useCase = CreateCashFlowRecordUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
                refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
                clockProvider = testClockProvider,
            )

            assertFailsWith<IllegalArgumentException> {
                useCase(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW,
                    amount = 100,
                    note = "早餐",
                    occurredAt = 59_000,
                    operationId = testOperationId(),
                )
            }
        }
    }

    @Test
    fun `transfer cannot be created before either account start minute`() {
        runBlocking {
            val accountRepository = InMemoryAccountRepository()
            val transactionRepository = InMemoryTransactionRepository()
            val fromAccountId = accountRepository.createAccount(
                Account(
                    name = "旧卡",
                    initialBalance = 0,
                    createdAt = 1_000,
                ),
            )
            val toAccountId = accountRepository.createAccount(
                Account(
                    name = "新卡",
                    initialBalance = 0,
                    createdAt = 121_000,
                ),
            )
            val useCase = CreateTransferRecordUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
                refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
                clockProvider = testClockProvider,
            )

            assertFailsWith<IllegalArgumentException> {
                useCase(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = 500,
                    note = "调拨",
                    occurredAt = 119_000,
                    operationId = testOperationId(),
                )
            }
        }
    }

    @Test
    fun `balance update cannot be created before account start minute`() {
        runBlocking {
            val accountRepository = InMemoryAccountRepository()
            val transactionRepository = InMemoryTransactionRepository()
            val accountId = accountRepository.createAccount(
                Account(
                    name = "储蓄卡",
                    initialBalance = 10_000,
                    createdAt = 61_000,
                ),
            )
            val useCase = UpdateBalanceUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
                resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
                refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository),
                clockProvider = testClockProvider,
            )

            assertFailsWith<IllegalArgumentException> {
                useCase(
                    accountId = accountId,
                    actualBalance = 9_000,
                    occurredAt = 59_000,
                    operationId = testOperationId(),
                )
            }
        }
    }
}
