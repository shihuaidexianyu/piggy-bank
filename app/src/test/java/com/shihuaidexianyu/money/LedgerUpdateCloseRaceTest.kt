package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LedgerUpdateCloseRaceTest {
    @Test
    fun allFourUpdatesRecheckAffectedAccountsInsideTransactionAfterConcurrentClose() = runBlocking {
        assertCashUpdateRejectedAfterClose()
        assertTransferUpdateRejectedAfterClose()
        assertBalanceUpdateRejectedAfterClose()
        assertAdjustmentUpdateRejectedAfterClose()
    }

    private suspend fun assertCashUpdateRejectedAfterClose() {
        val fixture = Fixture()
        val accountId = fixture.account("现金")
        val original = CashFlowRecord(
            accountId = accountId,
            direction = CashFlowDirection.INFLOW.value,
            amount = 100,
            note = "原始",
            occurredAt = 2,
            createdAt = 2,
            updatedAt = 2,
            operationId = "race-cash",
        )
        val id = fixture.delegate.insertCashFlowRecord(original).recordId
        val repository = fixture.closingRepository(accountId)
        val useCase = UpdateCashFlowRecordUseCase(
            fixture.accounts,
            repository,
            fixture.refresh,
            testClockProvider(100),
        )

        assertClosedRejected {
            useCase(id, accountId, CashFlowDirection.OUTFLOW, 200, "修改", 3)
        }
        assertEquals(original.copy(id = id), fixture.delegate.queryCashFlowRecordById(id))
    }

    private suspend fun assertTransferUpdateRejectedAfterClose() {
        val fixture = Fixture()
        val sourceId = fixture.account("来源")
        val targetId = fixture.account("目标")
        val original = TransferRecord(
            fromAccountId = sourceId,
            toAccountId = targetId,
            amount = 100,
            note = "原始",
            occurredAt = 2,
            createdAt = 2,
            updatedAt = 2,
            operationId = "race-transfer",
        )
        val id = fixture.delegate.insertTransferRecord(original).recordId
        val repository = fixture.closingRepository(targetId)
        val useCase = UpdateTransferRecordUseCase(
            fixture.accounts,
            repository,
            fixture.refresh,
            testClockProvider(100),
        )

        assertClosedRejected {
            useCase(id, sourceId, targetId, 200, "修改", 3)
        }
        assertEquals(original.copy(id = id), fixture.delegate.queryTransferRecordById(id))
    }

    private suspend fun assertBalanceUpdateRejectedAfterClose() {
        val fixture = Fixture()
        val accountId = fixture.account("现金")
        val original = BalanceUpdateRecord(
            accountId = accountId,
            actualBalance = 100,
            systemBalanceBeforeUpdate = 90,
            delta = 10,
            occurredAt = 2,
            createdAt = 2,
            updatedAt = 2,
            operationId = "race-balance-update",
        )
        val id = fixture.delegate.insertBalanceUpdateRecord(original).recordId
        val repository = fixture.closingRepository(accountId)
        val useCase = UpdateBalanceUpdateRecordUseCase(
            fixture.accounts,
            repository,
            ResolveBalanceUpdateContextUseCase(fixture.accounts, repository),
            fixture.refresh,
            testClockProvider(100),
        )

        assertClosedRejected { useCase(id, actualBalance = 200, occurredAt = 3) }
        assertEquals(original.copy(id = id), fixture.delegate.getBalanceUpdateRecordById(id))
    }

    private suspend fun assertAdjustmentUpdateRejectedAfterClose() {
        val fixture = Fixture()
        val accountId = fixture.account("现金")
        val original = BalanceAdjustmentRecord(
            accountId = accountId,
            delta = 10,
            occurredAt = 2,
            createdAt = 2,
            updatedAt = 2,
            operationId = "race-adjustment",
        )
        val id = fixture.delegate.insertBalanceAdjustmentRecord(original).recordId
        val repository = fixture.closingRepository(accountId)
        val useCase = UpdateBalanceAdjustmentUseCase(
            fixture.accounts,
            repository,
            fixture.refresh,
            testClockProvider(100),
        )

        assertClosedRejected { useCase(id, delta = 20, occurredAt = 3) }
        assertEquals(original.copy(id = id), fixture.delegate.getBalanceAdjustmentRecordById(id))
    }

    private suspend fun assertClosedRejected(block: suspend () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException> { block() }
        kotlin.test.assertTrue(error.message.orEmpty().startsWith("关闭账户不能"))
    }

    private class Fixture {
        val accounts = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val refresh = RefreshAccountActivityStateUseCase(accounts, delegate)

        suspend fun account(name: String): Long = accounts.createAccount(
            Account(name = name, initialBalance = 0, createdAt = 1, lastUsedAt = 1),
        )

        fun closingRepository(accountId: Long): TransactionRepository = object :
            TransactionRepository by delegate,
            LedgerAggregateRepository by delegate {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = delegate.runInTransaction {
                accounts.closeAccount(accountId, closedAt = 50)
                block()
            }
        }
    }
}
