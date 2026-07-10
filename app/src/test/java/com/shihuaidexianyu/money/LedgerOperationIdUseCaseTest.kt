package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerOperationIdUseCaseTest {
    private val clock = ClockProvider { 1_000L }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    @Test
    fun `all create commands replay after clock rollback`() = runBlocking {
        val clock = MutableClock(now = 1_000L)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val firstAccountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 0),
        )
        val secondAccountId = accountRepository.createAccount(
            Account(name = "储蓄", initialBalance = 0, createdAt = 0),
        )
        val refresh = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val createCash = CreateCashFlowRecordUseCase(accountRepository, transactionRepository, refresh, clock)
        val createTransfer = CreateTransferRecordUseCase(accountRepository, transactionRepository, refresh, clock)
        val updateBalance = UpdateBalanceUseCase(
            accountRepository,
            transactionRepository,
            ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository),
            refresh,
            clock,
        )
        val createAdjustment = CreateBalanceAdjustmentUseCase(
            accountRepository,
            transactionRepository,
            refresh,
            clock,
        )

        assertTrue(createCash(firstAccountId, CashFlowDirection.INFLOW, 100, "工资", 500, "cash-clock").inserted)
        assertTrue(createTransfer(firstAccountId, secondAccountId, 20, "转账", 500, "transfer-clock").inserted)
        assertTrue(updateBalance(firstAccountId, 75, 500, "balance-clock").insertResult.inserted)
        assertTrue(createAdjustment(firstAccountId, 5, 500, "adjustment-clock").inserted)

        clock.now = 499L

        assertFalse(createCash(firstAccountId, CashFlowDirection.INFLOW, 100, "工资", 500, "cash-clock").inserted)
        assertFalse(createTransfer(firstAccountId, secondAccountId, 20, "转账", 500, "transfer-clock").inserted)
        assertFalse(updateBalance(firstAccountId, 75, 500, "balance-clock").insertResult.inserted)
        assertFalse(createAdjustment(firstAccountId, 5, 500, "adjustment-clock").inserted)
    }

    @Test
    fun `create use cases persist caller operation ids and replay after account archive`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val firstAccountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 0),
        )
        val secondAccountId = accountRepository.createAccount(
            Account(name = "储蓄", initialBalance = 0, createdAt = 0),
        )
        val refresh = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val createCash = CreateCashFlowRecordUseCase(accountRepository, transactionRepository, refresh, clock)
        val createTransfer = CreateTransferRecordUseCase(accountRepository, transactionRepository, refresh, clock)
        val updateBalance = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
                accountRepository,
                transactionRepository,
            ),
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = clock,
        )
        val createAdjustment = CreateBalanceAdjustmentUseCase(
            accountRepository,
            transactionRepository,
            refresh,
            clock,
        )

        val cash = createCash(firstAccountId, CashFlowDirection.INFLOW, 100, "工资", 1, "cash-id")
        val transfer = createTransfer(firstAccountId, secondAccountId, 20, "转账", 2, "transfer-id")
        val balance = updateBalance(firstAccountId, 75, 3, "balance-id")
        val adjustment = createAdjustment(firstAccountId, 5, 4, "adjustment-id")

        assertTrue(cash.inserted)
        assertTrue(transfer.inserted)
        assertTrue(balance.insertResult.inserted)
        assertTrue(adjustment.inserted)
        assertEquals("cash-id", transactionRepository.queryAllCashFlowRecords().single().operationId)
        assertEquals("transfer-id", transactionRepository.queryAllTransferRecords().single().operationId)
        assertEquals("balance-id", transactionRepository.queryAllBalanceUpdateRecords().single().operationId)
        assertEquals("adjustment-id", transactionRepository.queryAllBalanceAdjustmentRecords().single().operationId)

        accountRepository.archiveAccount(firstAccountId, archivedAt = 900)
        accountRepository.archiveAccount(secondAccountId, archivedAt = 900)

        assertFalse(createCash(firstAccountId, CashFlowDirection.INFLOW, 100, "工资", 1, "cash-id").inserted)
        assertFalse(createTransfer(firstAccountId, secondAccountId, 20, "转账", 2, "transfer-id").inserted)
        assertFalse(updateBalance(firstAccountId, 75, 3, "balance-id").insertResult.inserted)
        assertFalse(createAdjustment(firstAccountId, 5, 4, "adjustment-id").inserted)
    }

    @Test
    fun `balance retry returns first evidence after intervening ledger mutation`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 0),
        )
        val refresh = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalance = UpdateBalanceUseCase(
            accountRepository,
            transactionRepository,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
                accountRepository,
                transactionRepository,
            ),
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = clock,
        )
        val createCash = CreateCashFlowRecordUseCase(
            accountRepository,
            transactionRepository,
            refresh,
            clock,
        )

        val first = updateBalance(accountId, actualBalance = 100, occurredAt = 100, operationId = "balance-command")
        createCash(accountId, CashFlowDirection.INFLOW, 50, "补录", 90, "cash-command")
        val replay = updateBalance(accountId, actualBalance = 100, occurredAt = 100, operationId = "balance-command")

        assertEquals(0, first.systemBalanceBeforeUpdate)
        assertEquals(100, first.delta)
        assertFalse(replay.insertResult.inserted)
        assertEquals(first.systemBalanceBeforeUpdate, replay.systemBalanceBeforeUpdate)
        assertEquals(first.delta, replay.delta)
        assertEquals(1, transactionRepository.queryAllBalanceUpdateRecords().size)
    }
}
