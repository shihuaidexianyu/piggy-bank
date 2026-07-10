package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.RecurringReminderRepositoryImpl
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloseAccountTransactionContractTest {
    private lateinit var database: MoneyDatabase
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var reminderRepository: RecurringReminderRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountRepository = AccountRepositoryImpl(database.accountDao())
        reminderRepository = RecurringReminderRepositoryImpl(
            dao = database.recurringReminderDao(),
            tickerFlow = flowOf(NOW),
        )
        transactionRepository = TransactionRepositoryImpl(
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
    fun closeFailureAfterReminderAndAccountWrites_rollsBackBothRoomRepositories() = runBlocking {
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        val reminderId = reminderRepository.insertReminder(
            RecurringReminder(
                name = "记账",
                type = ReminderType.MANUAL.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 10L,
                periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                periodValue = 1,
                periodMonth = null,
                nextDueAt = NOW + 1_000L,
                anchorDueAt = NOW + 1_000L,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )
        val failingAccountRepository = object : AccountRepository by accountRepository {
            override suspend fun closeAccount(accountId: Long, closedAt: Long) {
                accountRepository.closeAccount(accountId, closedAt)
                error("injected failure after account close write")
            }
        }
        val clock = ClockProvider { NOW }
        val close = CloseAccountUseCase(
            accountRepository = failingAccountRepository,
            reminderRepository = reminderRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepository = failingAccountRepository,
                ledgerAggregateRepository = transactionRepository,
                clockProvider = clock,
            ),
            transactionRunner = transactionRepository,
            clockProvider = clock,
            accountLifecycleCoordinator = AccountLifecycleCoordinator(),
        )

        var failure: Throwable? = null
        try {
            close(accountId)
        } catch (throwable: Throwable) {
            failure = throwable
        }

        assertTrue(failure is IllegalStateException)
        assertNull(accountRepository.getAccountById(accountId)?.closedAt)
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId))
        assertTrue(reminder.isEnabled)
        assertEquals(2L, reminder.updatedAt)
    }

    private companion object {
        const val NOW = 10_000L
    }
}
