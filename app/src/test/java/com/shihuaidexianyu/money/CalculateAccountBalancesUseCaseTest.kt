package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CalculateAccountBalancesUseCaseTest {
    @Test
    fun `batch balance at Long MAX_VALUE includes a record at that exact instant`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "边界账户", initialBalance = 10_000, createdAt = 1_000),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 500,
                note = "最大时间戳",
                occurredAt = Long.MAX_VALUE,
                createdAt = Long.MAX_VALUE,
                updatedAt = Long.MAX_VALUE,
                operationId = testOperationId(),
            ),
        )
        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val useCase = CalculateAccountBalancesUseCase(transactionRepository)

        assertEquals(
            mapOf(accountId to 10_500L),
            useCase(listOf(account), atTimeMillis = Long.MAX_VALUE),
        )
    }

    @Test
    fun `batch and single balances include exact current time and exclude future records`() = runBlocking {
        val currentTime = 5_000L
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "边界账户", initialBalance = 10_000, createdAt = 1_000),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 500,
                note = "当前时刻",
                occurredAt = currentTime,
                createdAt = currentTime,
                updatedAt = currentTime,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 9_000,
                note = "未来记录",
                occurredAt = currentTime + 1,
                createdAt = currentTime + 1,
                updatedAt = currentTime + 1,
                operationId = testOperationId(),
            ),
        )
        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val clockProvider = testClockProvider(currentTime)
        val single = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository, clockProvider)
        val batch = CalculateAccountBalancesUseCase(transactionRepository, clockProvider)

        val expected = mapOf(accountId to 10_500L)
        assertEquals(expected, mapOf(accountId to single(accountId)))
        assertEquals(expected, batch(listOf(account)))
    }

    @Test
    fun `batch balances match single account balances across record types`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val firstAccountId = accountRepository.createAccount(
            Account(name = "主账户", initialBalance = 10_000, createdAt = 1_000),
        )
        val secondAccountId = accountRepository.createAccount(
            Account(name = "备用金", initialBalance = 5_000, createdAt = 1_000),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = firstAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 2_000,
                note = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = firstAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 300,
                note = "餐饮",
                occurredAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = firstAccountId,
                toAccountId = secondAccountId,
                amount = 500,
                note = "调拨",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = firstAccountId,
                actualBalance = 12_000,
                systemBalanceBeforeUpdate = 11_200,
                delta = 800,
                occurredAt = 5_000,
                createdAt = 5_000,
                updatedAt = 5_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = secondAccountId,
                actualBalance = 6_000,
                systemBalanceBeforeUpdate = 5_500,
                delta = 500,
                occurredAt = 5_500,
                createdAt = 5_500,
                updatedAt = 5_500,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = firstAccountId,
                delta = 700,
                occurredAt = 6_000,
                createdAt = 6_000,
                updatedAt = 6_000,
                operationId = testOperationId(),
            ),
        )
        val deletedCashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = firstAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 9_999,
                note = "应忽略",
                occurredAt = 6_500,
                createdAt = 6_500,
                updatedAt = 6_500,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.softDeleteCashFlowRecord(deletedCashFlowId, 6_600)
        val deletedTransferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = secondAccountId,
                toAccountId = firstAccountId,
                amount = 9_999,
                note = "应忽略",
                occurredAt = 6_700,
                createdAt = 6_700,
                updatedAt = 6_700,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.softDeleteTransferRecord(deletedTransferId, 6_800)

        val accounts = listOf(
            accountRepository.getAccountById(firstAccountId) ?: error("missing account"),
            accountRepository.getAccountById(secondAccountId) ?: error("missing account"),
        )
        val single = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val batch = CalculateAccountBalancesUseCase(transactionRepository)

        assertEquals(singleExpected(accounts, single), batch(accounts))
        assertEquals(singleExpected(accounts, single, atTimeMillis = 5_250), batch(accounts, atTimeMillis = 5_250))
    }

    private suspend fun singleExpected(
        accounts: List<Account>,
        single: CalculateCurrentBalanceUseCase,
        atTimeMillis: Long = Long.MAX_VALUE,
    ): Map<Long, Long> {
        return accounts.associate { account -> account.id to single(account.id, atTimeMillis) }
    }
}
