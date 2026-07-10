package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountUseCaseTest {
    private val accountLifecycleCoordinator = AccountLifecycleCoordinator()

    @Test
    fun `reminder config write runs after the Room transaction completes`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderDelegate = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        var inRoomTransaction = false
        val trackingTransactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                inRoomTransaction = true
                return try {
                    block()
                } finally {
                    inRoomTransaction = false
                }
            }
        }
        val assertingReminderRepository = object : AccountReminderSettingsRepository by reminderDelegate {
            override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
                assertFalse(inRoomTransaction, "Preferences DataStore must not be awaited inside a Room transaction")
                reminderDelegate.updateReminderConfig(accountId, config)
            }
        }

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = assertingReminderRepository,
            transactionRunner = trackingTransactionRunner,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
        )(
            accountId = accountId,
            name = "现金账户",
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.MONDAY,
            ),
        )

        assertEquals("现金账户", accountRepository.getAccountById(accountId)?.name)
        assertEquals(
            BalanceUpdateReminderWeekday.MONDAY,
            reminderDelegate.getReminderConfig(accountId).weekday,
        )
    }

    @Test
    fun `close waits until reminder config write finishes after the account transaction`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderSettingsDelegate = InMemoryAccountReminderSettingsRepository()
        val recurringReminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        val configWriteStarted = CompletableDeferred<Unit>()
        val allowConfigWriteToFinish = CompletableDeferred<Unit>()
        val closeTransactionEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val blockingReminderRepository = object : AccountReminderSettingsRepository by reminderSettingsDelegate {
            override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
                events += "config-start"
                configWriteStarted.complete(Unit)
                allowConfigWriteToFinish.await()
                reminderSettingsDelegate.updateReminderConfig(accountId, config)
                events += "config-finish"
            }
        }
        val closeTransactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T =
                transactionRepository.runInTransaction {
                    events += "close-enter"
                    closeTransactionEntered.complete(Unit)
                    block()
                }
        }
        val update = UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = blockingReminderRepository,
            transactionRunner = transactionRepository,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
        )
        val close = CloseAccountUseCase(
            accountRepository = accountRepository,
            reminderRepository = recurringReminderRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepository,
                transactionRepository,
                testClockProvider,
            ),
            transactionRunner = closeTransactionRunner,
            clockProvider = testClockProvider,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
        )

        val updateJob = async(start = CoroutineStart.UNDISPATCHED) {
            update(
                accountId = accountId,
                name = "现金账户",
                balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                    weekday = BalanceUpdateReminderWeekday.MONDAY,
                ),
            )
        }
        assertTrue(configWriteStarted.isCompleted)
        assertEquals("现金账户", accountRepository.getAccountById(accountId)?.name)

        val closeJob = async(start = CoroutineStart.UNDISPATCHED) { close(accountId) }

        assertFalse(closeTransactionEntered.isCompleted)
        assertNull(accountRepository.getAccountById(accountId)?.closedAt)

        allowConfigWriteToFinish.complete(Unit)
        updateJob.await()
        closeJob.await()

        assertEquals(listOf("config-start", "config-finish", "close-enter"), events)
        assertTrue(requireNotNull(requireNotNull(accountRepository.getAccountById(accountId)).closedAt) > 0L)
    }

    @Test
    fun `edit rejects an account closed immediately before the transaction block`() = runBlocking {
        val accountDelegate = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountDelegate.createAccount(
            Account(
                name = "原账户",
                initialBalance = 0L,
                createdAt = 1L,
                colorName = "blue",
                iconName = "wallet",
            ),
        )
        val closeBeforeBlockRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                accountDelegate.closeAccount(accountId, 5_000L)
                return block()
            }
        }

        assertFailsWith<IllegalArgumentException> {
            UpdateAccountUseCase(
                accountDelegate,
                reminderRepository,
                closeBeforeBlockRunner,
                accountLifecycleCoordinator,
            )(
                accountId = accountId,
                name = "竞态改名",
                colorName = "purple",
                iconName = "bank",
            )
        }

        val closed = requireNotNull(accountDelegate.getAccountById(accountId))
        assertEquals(5_000L, closed.closedAt)
        assertEquals("原账户", closed.name)
        assertEquals("blue", closed.colorName)
        assertEquals("wallet", closed.iconName)
        assertEquals(BalanceUpdateReminderConfig(), reminderRepository.getReminderConfig(accountId))
    }

    @Test
    fun `changing account updates name and reminder config`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "银行卡",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
            transactionRunner = directTransactionRunner,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
        )(
            accountId = accountId,
            name = "理财账户",
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                period = BalanceUpdateReminderPeriod.MONTHLY,
                weekday = BalanceUpdateReminderWeekday.THURSDAY,
                monthDay = 25,
                hour = 20,
                minute = 15,
            ),
            colorName = "purple",
            iconName = "bank",
        )

        val updated = accountRepository.getAccountById(accountId)
        assertEquals("理财账户", updated?.name)
        assertEquals("purple", updated?.colorName)
        assertEquals("bank", updated?.iconName)
        assertEquals(
            BalanceUpdateReminderConfig(
                period = BalanceUpdateReminderPeriod.MONTHLY,
                weekday = BalanceUpdateReminderWeekday.THURSDAY,
                monthDay = 25,
                hour = 20,
                minute = 15,
            ),
            reminderRepository.getReminderConfig(accountId),
        )
    }

    @Test
    fun `changing account keeps updated reminder config`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "证券账户",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
            transactionRunner = directTransactionRunner,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
        )(
            accountId = accountId,
            name = "银行卡",
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.MONDAY,
                hour = 9,
                minute = 0,
            ),
        )

        assertEquals("银行卡", accountRepository.getAccountById(accountId)?.name)
        assertEquals(
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.MONDAY,
                hour = 9,
                minute = 0,
            ),
            reminderRepository.getReminderConfig(accountId),
        )
    }

    private val directTransactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
