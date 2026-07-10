package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveAccountDetailUseCaseTest {
    @Test
    fun `account detail stale status uses injected clock and zone`() = runBlocking {
        val now = Instant.parse("2026-04-10T14:30:00Z").toEpochMilli()
        val lastUpdatedAt = Instant.parse("2026-04-06T10:00:00Z").toEpochMilli()
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "时区边界账户",
                initialBalance = 10_000,
                createdAt = lastUpdatedAt,
                lastBalanceUpdateAt = lastUpdatedAt,
            ),
        )
        reminderSettingsRepository.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.FRIDAY,
                hour = 22,
                minute = 0,
            ),
        )
        val clockProvider = testClockProvider(now)
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            portableSettingsRepository = InMemoryPortableSettingsRepository(PortableSettings()),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepository,
                transactionRepository,
                clockProvider,
            ),
            clockProvider = clockProvider,
            zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
        )

        val snapshot = useCase().first()

        assertFalse(snapshot.isStale)
    }
}
