package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BalanceUpdateMutationUseCaseTest {
    @Test
    fun `editing balance update recalculates only its own delta and current balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            Account(
                name = "银行卡",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                note = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )

        updateBalanceUseCase(accountId, 11_000, 3_000, testOperationId())
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        updateBalanceUpdateRecordUseCase(
            recordId = recordId,
            actualBalance = 9_500,
            occurredAt = 1_500,
        )

        val updatedRecord = transactionRepository.getBalanceUpdateRecordById(recordId) ?: error("missing updated record")
        assertEquals(10_000, updatedRecord.systemBalanceBeforeUpdate)
        assertEquals(-500, updatedRecord.delta)
        assertEquals(11_500, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `deleting latest balance update restores last balance update timestamp`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "证券账户",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 99,
                toAccountId = accountId,
                amount = 20_000,
                note = "入金",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )

        updateBalanceUseCase(accountId, 130_000, 3_000, testOperationId())
        updateBalanceUseCase(accountId, 140_000, 5_000, testOperationId())
        val latestRecordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing latest record")

        deleteBalanceUpdateRecordUseCase(latestRecordId)

        assertEquals(3_000, accountRepository.getAccountById(accountId)?.lastBalanceUpdateAt)
        assertNull(transactionRepository.getBalanceUpdateRecordById(latestRecordId))
    }

    @Test
    fun `deleting balance update retains tombstone and restores current balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
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
                note = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                operationId = testOperationId(),
            ),
        )

        updateBalanceUseCase(accountId, 11_000, 3_000, testOperationId())
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        deleteBalanceUpdateRecordUseCase(recordId)
        deleteBalanceUpdateRecordUseCase(recordId)

        assertNull(transactionRepository.getBalanceUpdateRecordById(recordId))
        val storedRecord = transactionRepository.queryAllBalanceUpdateRecords().single()
        assertNotNull(storedRecord.deletedAt)
        assertEquals(-1_000, storedRecord.delta)
        assertNull(accountRepository.getAccountById(accountId)?.lastBalanceUpdateAt)
        assertEquals(12_000, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `inserting earlier balance update keeps later reconciliation delta fixed`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 1_000,
                note = "补贴",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
                operationId = testOperationId(),
            ),
        )

        updateBalanceUseCase(accountId, 13_000, 5_000, testOperationId())
        val laterRecordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing later record")

        updateBalanceUseCase(accountId, 9_000, 3_000, testOperationId())

        val laterRecord = transactionRepository.getBalanceUpdateRecordById(laterRecordId) ?: error("missing fixed record")
        assertEquals(11_000, laterRecord.systemBalanceBeforeUpdate)
        assertEquals(2_000, laterRecord.delta)
    }

    @Test
    fun `deleting earlier balance update keeps surviving reconciliation delta fixed`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "副账户",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        val earlierRecordId = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 9_000,
            occurredAt = 3_000,
            operationId = testOperationId(),
        ).let {
            transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
                .first { record -> record.occurredAt == 3_000L }
                .id
        }
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 1_000,
                note = "返现",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
                operationId = testOperationId(),
            ),
        )
        updateBalanceUseCase(accountId, 13_000, 5_000, testOperationId())
        val laterRecordId = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .first { record -> record.occurredAt == 5_000L }
            .id

        deleteBalanceUpdateRecordUseCase(earlierRecordId)

        val laterRecord = transactionRepository.getBalanceUpdateRecordById(laterRecordId) ?: error("missing surviving record")
        assertEquals(10_000, laterRecord.systemBalanceBeforeUpdate)
        assertEquals(3_000, laterRecord.delta)
    }

    @Test
    fun `cash flow mutations before balance update do not rewrite reconciliation event`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val createCashFlow = CreateCashFlowRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val updateCashFlow = UpdateCashFlowRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val deleteCashFlow = DeleteCashFlowRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 10_000, createdAt = 1_000),
        )
        updateBalanceUseCase(accountId, 10_000, 5_000, testOperationId())
        val balanceUpdateId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        val cashFlowId = createCashFlow(
            accountId = accountId,
            direction = CashFlowDirection.INFLOW,
            amount = 2_000,
            note = "工资",
            occurredAt = 3_000,
            operationId = testOperationId(),
        ).recordId
        assertBalanceUpdate(transactionRepository, balanceUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertEquals(12_000, calculateCurrentBalanceUseCase(accountId))

        updateCashFlow(
            recordId = cashFlowId,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 500,
            note = "午餐",
            occurredAt = 3_000,
        )
        assertBalanceUpdate(transactionRepository, balanceUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertEquals(9_500, calculateCurrentBalanceUseCase(accountId))

        deleteCashFlow(cashFlowId)

        assertBalanceUpdate(transactionRepository, balanceUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertEquals(10_000, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `transfer mutations before balance update do not rewrite reconciliation events`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val createTransfer = CreateTransferRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val updateTransfer = UpdateTransferRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
            clockProvider = testClockProvider,
        )
        val deleteTransfer = DeleteTransferRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val firstAccountId = accountRepository.createAccount(
            Account(name = "银行卡", initialBalance = 10_000, createdAt = 1_000),
        )
        val secondAccountId = accountRepository.createAccount(
            Account(name = "零钱", initialBalance = 5_000, createdAt = 1_000),
        )
        updateBalanceUseCase(firstAccountId, 10_000, 5_000, testOperationId())
        updateBalanceUseCase(secondAccountId, 5_000, 5_000, testOperationId())
        val firstUpdateId = transactionRepository.getLatestBalanceUpdate(firstAccountId)?.id ?: error("missing first")
        val secondUpdateId = transactionRepository.getLatestBalanceUpdate(secondAccountId)?.id ?: error("missing second")

        val transferId = createTransfer(
            fromAccountId = firstAccountId,
            toAccountId = secondAccountId,
            amount = 1_000,
            note = "调拨",
            occurredAt = 3_000,
            operationId = testOperationId(),
        ).recordId
        assertBalanceUpdate(transactionRepository, firstUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertBalanceUpdate(transactionRepository, secondUpdateId, systemBalanceBeforeUpdate = 5_000, delta = 0)
        assertEquals(9_000, calculateCurrentBalanceUseCase(firstAccountId))
        assertEquals(6_000, calculateCurrentBalanceUseCase(secondAccountId))

        updateTransfer(
            recordId = transferId,
            fromAccountId = secondAccountId,
            toAccountId = firstAccountId,
            amount = 500,
            note = "调回",
            occurredAt = 3_000,
        )
        assertBalanceUpdate(transactionRepository, firstUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertBalanceUpdate(transactionRepository, secondUpdateId, systemBalanceBeforeUpdate = 5_000, delta = 0)
        assertEquals(10_500, calculateCurrentBalanceUseCase(firstAccountId))
        assertEquals(4_500, calculateCurrentBalanceUseCase(secondAccountId))

        deleteTransfer(transferId)

        assertBalanceUpdate(transactionRepository, firstUpdateId, systemBalanceBeforeUpdate = 10_000, delta = 0)
        assertBalanceUpdate(transactionRepository, secondUpdateId, systemBalanceBeforeUpdate = 5_000, delta = 0)
        assertEquals(10_000, calculateCurrentBalanceUseCase(firstAccountId))
        assertEquals(5_000, calculateCurrentBalanceUseCase(secondAccountId))
    }

    private suspend fun assertBalanceUpdate(
        transactionRepository: InMemoryTransactionRepository,
        recordId: Long,
        systemBalanceBeforeUpdate: Long,
        delta: Long,
    ) {
        val record = transactionRepository.getBalanceUpdateRecordById(recordId) ?: error("missing balance update")
        assertEquals(systemBalanceBeforeUpdate, record.systemBalanceBeforeUpdate)
        assertEquals(delta, record.delta)
    }
}
