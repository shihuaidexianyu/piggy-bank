package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountUseCaseTest {
    @Test
    fun `changing account updates name and reminder config`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
        )(
            accountId = accountId,
            name = "理财账户",
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.THURSDAY,
                hour = 20,
                minute = 15,
            ),
        )

        val updated = accountRepository.getAccountById(accountId)
        assertEquals("理财账户", updated?.name)
        assertEquals(
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.THURSDAY,
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
            AccountEntity(
                name = "证券账户",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )

        UpdateAccountUseCase(
            accountRepository = accountRepository,
            accountReminderSettingsRepository = reminderRepository,
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
}

