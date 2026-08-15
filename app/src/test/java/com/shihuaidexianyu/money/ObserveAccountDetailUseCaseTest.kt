package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
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

    @Test
    fun `month investment delta sums only this month's reconciliation deltas`() = runBlocking {
        val now = Instant.parse("2026-04-10T14:30:00Z").toEpochMilli()
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "定投", initialBalance = 0, createdAt = 1L),
        )
        val otherAccountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 1L),
        )
        fun balanceUpdate(targetAccountId: Long, delta: Long, occurredAt: String, opId: String): BalanceUpdateRecord {
            val at = Instant.parse(occurredAt).toEpochMilli()
            return BalanceUpdateRecord(
                accountId = targetAccountId,
                actualBalance = 0L,
                systemBalanceBeforeUpdate = 0L,
                delta = delta,
                occurredAt = at,
                createdAt = at,
                updatedAt = at,
                operationId = opId,
            )
        }
        // In-month deltas (+300, -100) sum to 200; last month and other accounts are excluded.
        transactionRepository.insertBalanceUpdateRecord(balanceUpdate(accountId, 300L, "2026-04-02T10:00:00Z", "op-apr-gain"))
        transactionRepository.insertBalanceUpdateRecord(balanceUpdate(accountId, -100L, "2026-04-09T10:00:00Z", "op-apr-loss"))
        transactionRepository.insertBalanceUpdateRecord(balanceUpdate(accountId, 500L, "2026-03-15T10:00:00Z", "op-mar"))
        transactionRepository.insertBalanceUpdateRecord(balanceUpdate(otherAccountId, 700L, "2026-04-03T10:00:00Z", "op-other"))

        val clockProvider = testClockProvider(now)
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
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

        assertEquals(200L, snapshot.monthInvestmentDelta)
    }
}
