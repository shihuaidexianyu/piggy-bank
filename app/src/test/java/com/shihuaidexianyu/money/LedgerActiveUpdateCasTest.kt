package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.nextLedgerMutationTimestamp
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerActiveUpdateCasTest {
    @Test
    fun `active compare and set rejects stale timestamp operation change and tombstone for all kinds`() = runBlocking {
        val repository = InMemoryTransactionRepository()

        val cashId = repository.insertCashFlowRecord(cash()).recordId
        val cash = requireNotNull(repository.queryCashFlowRecordById(cashId))
        assertFalse(repository.updateCashFlowRecord(cash.copy(amount = 2, updatedAt = 20), expectedUpdatedAt = 9))
        assertFalse(
            repository.updateCashFlowRecord(
                cash.copy(operationId = "changed", updatedAt = 20),
                expectedUpdatedAt = cash.updatedAt,
            ),
        )
        repository.softDeleteCashFlowRecord(cashId, 30)
        assertFalse(repository.updateCashFlowRecord(cash.copy(amount = 2, updatedAt = 40), cash.updatedAt))
        assertEquals(30, repository.queryCashFlowRecordByOperationId(cash.operationId)?.deletedAt)

        val transferId = repository.insertTransferRecord(transfer()).recordId
        val transfer = requireNotNull(repository.queryTransferRecordById(transferId))
        assertFalse(repository.updateTransferRecord(transfer.copy(amount = 2, updatedAt = 20), 9))
        assertFalse(repository.updateTransferRecord(transfer.copy(operationId = "changed", updatedAt = 20), 10))
        repository.softDeleteTransferRecord(transferId, 30)
        assertFalse(repository.updateTransferRecord(transfer.copy(amount = 2, updatedAt = 40), 10))
        assertEquals(30, repository.queryTransferRecordByOperationId(transfer.operationId)?.deletedAt)

        val updateId = repository.insertBalanceUpdateRecord(balanceUpdate()).recordId
        val update = requireNotNull(repository.getBalanceUpdateRecordById(updateId))
        assertFalse(repository.updateBalanceUpdateRecord(update.copy(actualBalance = 2, updatedAt = 20), 9))
        assertFalse(repository.updateBalanceUpdateRecord(update.copy(operationId = "changed", updatedAt = 20), 10))
        repository.deleteBalanceUpdateRecord(updateId, 30)
        assertFalse(repository.updateBalanceUpdateRecord(update.copy(actualBalance = 2, updatedAt = 40), 10))
        assertEquals(30, repository.queryBalanceUpdateRecordByOperationId(update.operationId)?.deletedAt)

        val adjustmentId = repository.insertBalanceAdjustmentRecord(adjustment()).recordId
        val adjustment = requireNotNull(repository.getBalanceAdjustmentRecordById(adjustmentId))
        assertFalse(repository.updateBalanceAdjustmentRecord(adjustment.copy(delta = 2, updatedAt = 20), 9))
        assertFalse(repository.updateBalanceAdjustmentRecord(adjustment.copy(operationId = "changed", updatedAt = 20), 10))
        repository.deleteBalanceAdjustmentRecord(adjustmentId, 30)
        assertFalse(repository.updateBalanceAdjustmentRecord(adjustment.copy(delta = 2, updatedAt = 40), 10))
        assertEquals(30, repository.queryBalanceAdjustmentRecordByOperationId(adjustment.operationId)?.deletedAt)
    }

    @Test
    fun `active compare and set updates semantic fields once`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val id = repository.insertCashFlowRecord(cash()).recordId
        val existing = requireNotNull(repository.queryCashFlowRecordById(id))

        assertTrue(repository.updateCashFlowRecord(existing.copy(amount = 2, updatedAt = 11), 10))
        assertEquals(2, repository.queryCashFlowRecordById(id)?.amount)
        assertFalse(repository.updateCashFlowRecord(existing.copy(amount = 3, updatedAt = 12), 10))
    }

    @Test
    fun `mutation timestamp is strictly increasing and detects overflow`() {
        assertEquals(101, nextLedgerMutationTimestamp(now = 100, storedUpdatedAt = 100))
        assertEquals(101, nextLedgerMutationTimestamp(now = 99, storedUpdatedAt = 100))
        assertEquals(200, nextLedgerMutationTimestamp(now = 200, storedUpdatedAt = 100))
        assertFailsWith<IllegalStateException> {
            nextLedgerMutationTimestamp(now = Long.MAX_VALUE, storedUpdatedAt = Long.MAX_VALUE)
        }
    }

    @Test
    fun `all update use cases translate rejected compare and set into changed exception`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val firstAccountId = accounts.createAccount(Account(name = "一号", initialBalance = 0, createdAt = 0))
        val secondAccountId = accounts.createAccount(Account(name = "二号", initialBalance = 0, createdAt = 0))
        val delegate = InMemoryTransactionRepository()
        val cashId = delegate.insertCashFlowRecord(cash().copy(accountId = firstAccountId)).recordId
        val transferId = delegate.insertTransferRecord(
            transfer().copy(fromAccountId = firstAccountId, toAccountId = secondAccountId),
        ).recordId
        val updateId = delegate.insertBalanceUpdateRecord(balanceUpdate().copy(accountId = firstAccountId)).recordId
        val adjustmentId = delegate.insertBalanceAdjustmentRecord(adjustment().copy(accountId = firstAccountId)).recordId
        val repository = RejectingUpdateRepository(delegate)
        val refresh = RefreshAccountActivityStateUseCase(accounts, repository)
        val clock = ClockProvider { 100 }

        assertFailsWith<LedgerRecordChangedException> {
            UpdateCashFlowRecordUseCase(accounts, repository, refresh, clock)(
                recordId = cashId,
                accountId = firstAccountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 2,
                note = "修改",
                occurredAt = 2,
            )
        }
        assertFailsWith<LedgerRecordChangedException> {
            UpdateTransferRecordUseCase(accounts, repository, refresh, clock)(
                recordId = transferId,
                fromAccountId = firstAccountId,
                toAccountId = secondAccountId,
                amount = 2,
                note = "修改",
                occurredAt = 2,
            )
        }
        assertFailsWith<LedgerRecordChangedException> {
            UpdateBalanceUpdateRecordUseCase(
                accounts,
                repository,
                ResolveBalanceUpdateContextUseCase(accounts, repository),
                refresh,
                clock,
            )(
                recordId = updateId,
                actualBalance = 2,
                occurredAt = 2,
            )
        }
        assertFailsWith<LedgerRecordChangedException> {
            UpdateBalanceAdjustmentUseCase(accounts, repository, refresh, clock)(
                recordId = adjustmentId,
                delta = 2,
                occurredAt = 2,
            )
        }

        assertEquals(1, delegate.queryCashFlowRecordById(cashId)?.amount)
        assertEquals(1, delegate.queryTransferRecordById(transferId)?.amount)
        assertEquals(1, delegate.getBalanceUpdateRecordById(updateId)?.delta)
        assertEquals(1, delegate.getBalanceAdjustmentRecordById(adjustmentId)?.delta)
    }

    private class RejectingUpdateRepository(
        delegate: TransactionRepository,
    ) : TransactionRepository by delegate {
        override suspend fun updateCashFlowRecord(record: CashFlowRecord, expectedUpdatedAt: Long): Boolean = false
        override suspend fun updateTransferRecord(record: TransferRecord, expectedUpdatedAt: Long): Boolean = false
        override suspend fun updateBalanceUpdateRecord(
            record: BalanceUpdateRecord,
            expectedUpdatedAt: Long,
        ): Boolean = false

        override suspend fun updateBalanceAdjustmentRecord(
            record: BalanceAdjustmentRecord,
            expectedUpdatedAt: Long,
        ): Boolean = false
    }

    private fun cash() = CashFlowRecord(
        accountId = 1, direction = "inflow", amount = 1, note = "n", occurredAt = 1,
        createdAt = 10, updatedAt = 10, operationId = "cash",
    )

    private fun transfer() = TransferRecord(
        fromAccountId = 1, toAccountId = 2, amount = 1, note = "n", occurredAt = 1,
        createdAt = 10, updatedAt = 10, operationId = "transfer",
    )

    private fun balanceUpdate() = BalanceUpdateRecord(
        accountId = 1, actualBalance = 1, systemBalanceBeforeUpdate = 0, delta = 1, occurredAt = 1,
        createdAt = 10, updatedAt = 10, operationId = "update",
    )

    private fun adjustment() = BalanceAdjustmentRecord(
        accountId = 1, delta = 1, occurredAt = 1, createdAt = 10, updatedAt = 10,
        operationId = "adjustment",
    )
}
