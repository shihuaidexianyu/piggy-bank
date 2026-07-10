package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerOverflowException
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerAggregateRepositoryContractTest {
    private lateinit var database: MoneyDatabase
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var ledger: TransactionRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = AccountRepositoryImpl(database.accountDao())
        ledger = TransactionRepositoryImpl(
            database = database,
            cashFlowRecordDao = database.cashFlowRecordDao(),
            transferRecordDao = database.transferRecordDao(),
            balanceUpdateRecordDao = database.balanceUpdateRecordDao(),
            balanceAdjustmentRecordDao = database.balanceAdjustmentRecordDao(),
            historyRecordDao = database.historyRecordDao(),
            ledgerAggregateDao = database.ledgerAggregateDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomAggregate_usesFlooredOpeningAndPreservesHalfOpenBoundaries() = runBlocking {
        val accountId = accounts.createAccount(
            Account(name = "现金", initialBalance = 10L, createdAt = 95_000L),
        )
        val account = requireNotNull(accounts.getAccountById(accountId))
        ledger.insertCashFlowRecord(cash(accountId, 5L, 59_999L, "before-opening"))
        ledger.insertCashFlowRecord(cash(accountId, 7L, 60_000L, "at-opening"))
        ledger.insertCashFlowRecord(cash(accountId, 11L, 120_000L, "right-boundary"))

        assertEquals(
            7L,
            ledger.queryBefore(listOf(account), endExclusive = 120_000L).getValue(accountId).inflow,
        )
        assertEquals(
            18L,
            ledger.queryBefore(listOf(account), endExclusive = 120_001L).getValue(accountId).inflow,
        )
    }

    @Test
    fun roomAggregate_handlesTransfersFixedDeltaActivityAndMAXModes() = runBlocking {
        val firstId = accounts.createAccount(Account(name = "A", initialBalance = 0L, createdAt = 0L))
        val secondId = accounts.createAccount(Account(name = "B", initialBalance = 0L, createdAt = 0L))
        val requested = listOf(
            requireNotNull(accounts.getAccountById(firstId)),
            requireNotNull(accounts.getAccountById(secondId)),
        )
        ledger.insertTransferRecord(transfer(firstId, secondId, 9L, 20L, "transfer"))
        ledger.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = firstId,
                actualBalance = 5_000L,
                systemBalanceBeforeUpdate = -5_000L,
                delta = -3L,
                occurredAt = 25L,
                createdAt = 25L,
                updatedAt = 25L,
                operationId = "fixed-delta",
            ),
        )
        ledger.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = secondId,
                delta = 4L,
                occurredAt = 30L,
                createdAt = 30L,
                updatedAt = 30L,
                operationId = "adjustment",
            ),
        )
        ledger.insertCashFlowRecord(cash(firstId, 2L, Long.MAX_VALUE, "max"))

        val beforeMax = ledger.queryBefore(requested, Long.MAX_VALUE)
        val atMax = ledger.queryAt(requested, Long.MAX_VALUE)

        assertEquals(9L, beforeMax.getValue(firstId).transferOut)
        assertEquals(9L, beforeMax.getValue(secondId).transferIn)
        assertEquals(-3L, beforeMax.getValue(firstId).reconciliation)
        assertEquals(4L, beforeMax.getValue(secondId).manualAdjustment)
        assertEquals(0L, beforeMax.getValue(firstId).inflow)
        assertEquals(2L, atMax.getValue(firstId).inflow)
        assertEquals(25L, ledger.queryActivityMaxima(firstId).maxBalanceUpdateOccurredAt)
        assertEquals(Long.MAX_VALUE, ledger.queryActivityMaxima(firstId).maxActiveOccurredAt)
    }

    @Test
    fun roomStatsSum_translatesSqliteIntegerOverflowToDomainFailure() = runBlocking {
        val accountId = accounts.createAccount(Account(name = "统计溢出", initialBalance = 0L, createdAt = 0L))
        ledger.insertCashFlowRecord(cash(accountId, Long.MAX_VALUE, 1L, "stats-max"))
        ledger.insertCashFlowRecord(cash(accountId, 1L, 2L, "stats-overflow"))

        var translated = false
        try {
            ledger.sumCashInflowBetween(startInclusive = 0L, endExclusive = 3L)
        } catch (_: LedgerOverflowException) {
            translated = true
        }
        assertTrue(translated)
    }

    @Test
    fun sqliteIntegerOverflow_isTranslatedToDomainFailure() = runBlocking {
        val accountId = accounts.createAccount(Account(name = "A", initialBalance = 0L, createdAt = 0L))
        val account = requireNotNull(accounts.getAccountById(accountId))
        ledger.insertCashFlowRecord(cash(accountId, Long.MAX_VALUE, 1L, "max"))
        ledger.insertCashFlowRecord(cash(accountId, 1L, 2L, "overflow"))

        var failure: Throwable? = null
        try {
            ledger.queryBefore(listOf(account), 3L)
        } catch (throwable: Throwable) {
            failure = throwable
        }
        assertTrue(failure is LedgerOverflowException)
    }

    private fun cash(accountId: Long, amount: Long, occurredAt: Long, operationId: String) = CashFlowRecord(
        accountId = accountId,
        direction = CashFlowDirection.INFLOW.value,
        amount = amount,
        note = operationId,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )

    private fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        occurredAt: Long,
        operationId: String,
    ) = TransferRecord(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
        note = operationId,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = operationId,
    )
}
