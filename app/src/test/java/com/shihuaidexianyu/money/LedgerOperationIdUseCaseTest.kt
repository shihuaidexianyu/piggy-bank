package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LedgerOperationIdUseCaseTest {
    @Test
    fun `each create invocation generates a distinct non-empty UUID operation id`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val firstAccountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 0),
        )
        val secondAccountId = accountRepository.createAccount(
            Account(name = "储蓄", initialBalance = 0, createdAt = 0),
        )
        val refresh = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)

        CreateCashFlowRecordUseCase(accountRepository, transactionRepository, refresh)(
            accountId = firstAccountId,
            direction = CashFlowDirection.INFLOW,
            amount = 100,
            note = "工资",
            occurredAt = 1,
        )
        CreateTransferRecordUseCase(accountRepository, transactionRepository, refresh)(
            fromAccountId = firstAccountId,
            toAccountId = secondAccountId,
            amount = 20,
            note = "转账",
            occurredAt = 2,
        )
        UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
                accountRepository,
                transactionRepository,
            ),
            refreshAccountActivityStateUseCase = refresh,
        )(
            accountId = firstAccountId,
            actualBalance = 75,
            occurredAt = 3,
        )
        CreateBalanceAdjustmentUseCase(accountRepository, transactionRepository, refresh)(
            accountId = firstAccountId,
            delta = 5,
            occurredAt = 4,
        )

        val operationIds = listOf(
            transactionRepository.queryAllCashFlowRecords().single().operationId,
            transactionRepository.queryAllTransferRecords().single().operationId,
            transactionRepository.queryAllBalanceUpdateRecords().single().operationId,
            transactionRepository.queryAllBalanceAdjustmentRecords().single().operationId,
        )
        assertTrue(operationIds.all(String::isNotBlank))
        operationIds.forEach(UUID::fromString)
        assertEquals(operationIds.size, operationIds.toSet().size)
    }
}
