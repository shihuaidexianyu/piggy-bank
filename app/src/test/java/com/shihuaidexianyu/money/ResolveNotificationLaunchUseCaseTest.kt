package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchDestination
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.domain.usecase.ResolveNotificationLaunchUseCase
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test

class ResolveNotificationLaunchUseCaseTest {
    @Test
    fun `changed occurrence routes to reminder center with changed status`() = runBlocking {
        val fixture = Fixture()
        val id = fixture.addReminder(dueAt = 100)

        val result = fixture.resolve(NotificationLaunchIdentity.Recurring(id, expectedDueAt = 99))

        assertEquals(NotificationLaunchDestination.ReminderCenter(stateChanged = true), result)
    }

    @Test
    fun `current occurrence routes to editable processing preview`() = runBlocking {
        val fixture = Fixture()
        val id = fixture.addReminder(dueAt = 100)

        val result = fixture.resolve(NotificationLaunchIdentity.Recurring(id, expectedDueAt = 100))

        assertEquals(id, (result as NotificationLaunchDestination.ProcessReminder).reminder.id)
    }

    @Test
    fun `fresh balance identity routes to reminder center as changed`() = runBlocking {
        val fixture = Fixture()
        fixture.configs.updateReminderConfig(fixture.accountId, BalanceUpdateReminderConfig(hour = 0, minute = 0))
        fixture.accounts.updateLastBalanceUpdateAt(fixture.accountId, 1_000)

        val result = fixture.resolve(NotificationLaunchIdentity.Balance(fixture.accountId))

        assertEquals(NotificationLaunchDestination.ReminderCenter(stateChanged = true), result)
    }

    private class Fixture {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val configs = InMemoryAccountReminderSettingsRepository()
        val accountId = runBlocking {
            accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        }
        private val useCase = ResolveNotificationLaunchUseCase(
            accounts,
            reminders,
            configs,
            clockProvider = { 1_000 },
            zoneIdProvider = { ZoneId.of("Asia/Shanghai") },
        )

        suspend fun resolve(identity: NotificationLaunchIdentity) = useCase(identity)

        suspend fun addReminder(dueAt: Long): Long = reminders.insertReminder(
            RecurringReminder(
                name = "bill",
                type = "manual",
                accountId = accountId,
                direction = "outflow",
                amount = 100,
                periodType = "custom_days",
                periodValue = 1,
                periodMonth = null,
                nextDueAt = dueAt,
                anchorDueAt = dueAt,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }
}
