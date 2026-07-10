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

class BalanceCalculationParityTest {
    @Test
    fun `single and batch balance calculations stay equal across mixed ledger records`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val primaryId = accountRepository.createAccount(
            Account(name = "主账户", initialBalance = 10_000, createdAt = 1_000),
        )
        val secondaryId = accountRepository.createAccount(
            Account(name = "备用账户", initialBalance = 5_000, createdAt = 1_000),
        )
        val single = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val batch = CalculateAccountBalancesUseCase(transactionRepository)

        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = primaryId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 2_500,
                note = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = primaryId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 800,
                note = "餐饮",
                occurredAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = secondaryId,
                toAccountId = primaryId,
                amount = 300,
                note = "转入",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = primaryId,
                toAccountId = secondaryId,
                amount = 700,
                note = "转出",
                occurredAt = 4_500,
                createdAt = 4_500,
                updatedAt = 4_500,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = primaryId,
                delta = 200,
                occurredAt = 5_000,
                createdAt = 5_000,
                updatedAt = 5_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = primaryId,
                actualBalance = 13_000,
                systemBalanceBeforeUpdate = 11_500,
                delta = 1_500,
                occurredAt = 6_000,
                createdAt = 6_000,
                updatedAt = 6_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = primaryId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 400,
                note = "退款",
                occurredAt = 7_000,
                createdAt = 7_000,
                updatedAt = 7_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = primaryId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 100,
                note = "零食",
                occurredAt = 7_500,
                createdAt = 7_500,
                updatedAt = 7_500,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = secondaryId,
                toAccountId = primaryId,
                amount = 50,
                note = "找零",
                occurredAt = 8_000,
                createdAt = 8_000,
                updatedAt = 8_000,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = primaryId,
                toAccountId = secondaryId,
                amount = 75,
                note = "归还",
                occurredAt = 8_500,
                createdAt = 8_500,
                updatedAt = 8_500,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = primaryId,
                delta = -25,
                occurredAt = 9_000,
                createdAt = 9_000,
                updatedAt = 9_000,
                operationId = testOperationId(),
            ),
        )

        val accounts = listOf(
            requireNotNull(accountRepository.getAccountById(primaryId)),
            requireNotNull(accountRepository.getAccountById(secondaryId)),
        )

        assertEquals(
            accounts.associate { account -> account.id to single(account.id, atTimeMillis = 5_500) },
            batch(accounts, atTimeMillis = 5_500),
        )
        assertEquals(
            accounts.associate { account -> account.id to single(account.id) },
            batch(accounts),
        )
        assertEquals(13_250, single(primaryId))
        assertEquals(5_425, single(secondaryId))
    }
}
