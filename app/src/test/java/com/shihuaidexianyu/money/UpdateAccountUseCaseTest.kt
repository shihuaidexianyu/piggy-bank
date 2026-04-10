package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountUseCaseTest {
    @Test
    fun `changing account updates group and reminder config`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "银行卡",
                groupType = "bank",
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
            groupType = AccountGroupType.INVESTMENT,
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.THURSDAY,
                hour = 20,
                minute = 15,
            ),
        )

        val updated = accountRepository.getAccountById(accountId)
        assertEquals("理财账户", updated?.name)
        assertEquals("investment", updated?.groupType)
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
    fun `changing away from investment keeps updated config`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryAccountReminderSettingsRepository()
        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = "证券账户",
                groupType = "investment",
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
            groupType = AccountGroupType.BANK,
            balanceUpdateReminderConfig = BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.MONDAY,
                hour = 9,
                minute = 0,
            ),
        )

        assertEquals("bank", accountRepository.getAccountById(accountId)?.groupType)
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

