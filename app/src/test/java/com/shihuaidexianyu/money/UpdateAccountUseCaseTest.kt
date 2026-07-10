package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountUseCaseTest {
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
            UpdateAccountUseCase(accountDelegate, reminderRepository, closeBeforeBlockRunner)(
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
