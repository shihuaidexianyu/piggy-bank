package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CreateAccountUseCaseTest {
    @Test
    fun `blank name is rejected`() = runBlocking {
        val useCase = CreateAccountUseCase(
            accountRepository = InMemoryAccountRepository(),
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(name = "   ", initialBalance = 0)
        }

        assertEquals("账户名称不能为空", error.message)
    }

    @Test
    fun `duplicate active name is rejected`() = runBlocking {
        val repository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val useCase = CreateAccountUseCase(
            accountRepository = repository,
            accountReminderSettingsRepository = reminderRepository,
        )
        useCase(
            name = "现金",
            initialBalance = 100,
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(weekday = BalanceUpdateReminderWeekday.MONDAY),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(
                name = "现金",
                initialBalance = 200,
                balanceUpdateReminderConfig = BalanceUpdateReminderConfig(weekday = BalanceUpdateReminderWeekday.MONDAY),
            )
        }

        assertEquals("已存在同名账户", error.message)
    }

    @Test
    fun `custom reminder config is persisted alongside account`() = runBlocking {
        val repository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val useCase = CreateAccountUseCase(
            accountRepository = repository,
            accountReminderSettingsRepository = reminderRepository,
        )

        val accountId = useCase(
            name = "信用卡",
            initialBalance = 100,
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.FRIDAY,
                hour = 21,
                minute = 30,
            ),
        )

        assertEquals(
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.FRIDAY,
                hour = 21,
                minute = 30,
            ),
            reminderRepository.getReminderConfig(accountId),
        )
    }

    @Test
    fun `account color setting is normalized on create`() = runBlocking {
        val repository = InMemoryAccountRepository()
        val useCase = CreateAccountUseCase(
            accountRepository = repository,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
        )

        val accountId = useCase(
            name = "旅行基金",
            initialBalance = 100,
            colorName = "teal",
        )
        val fallbackId = useCase(
            name = "默认账户",
            initialBalance = 100,
            colorName = "missing",
        )

        assertEquals("teal", repository.getAccountById(accountId)?.colorName)
        assertEquals(DEFAULT_ACCOUNT_COLOR_NAME, repository.getAccountById(fallbackId)?.colorName)
    }
}

