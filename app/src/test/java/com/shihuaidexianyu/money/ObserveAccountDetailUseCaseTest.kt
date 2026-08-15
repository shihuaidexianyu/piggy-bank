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
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.AccountDetailRecordKind
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

    @Test
    fun `recent records carry the running book balance of the viewed account`() = runBlocking {
        val now = Instant.parse("2026-04-10T14:30:00Z").toEpochMilli()
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "主账户", initialBalance = 1_000L, createdAt = 1L),
        )
        val otherAccountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        fun at(minute: Long): Long = Instant.parse("2026-04-10T10:00:00Z").toEpochMilli() + minute * 60_000L
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 500L,
                note = "工资",
                occurredAt = at(10),
                createdAt = at(10),
                updatedAt = at(10),
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = accountId,
                toAccountId = otherAccountId,
                amount = 300L,
                note = "",
                occurredAt = at(20),
                createdAt = at(20),
                updatedAt = at(20),
                operationId = testOperationId(),
            ),
        )
        // A zero-delta check changes nothing but still reports the balance it confirmed.
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 1_200L,
                systemBalanceBeforeUpdate = 1_200L,
                delta = 0L,
                occurredAt = at(25),
                createdAt = at(25),
                updatedAt = at(25),
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 100L,
                note = "午餐",
                occurredAt = at(30),
                createdAt = at(30),
                updatedAt = at(30),
                operationId = testOperationId(),
            ),
        )
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

        // Newest first: expense 1_100, zero-delta check 1_200, outgoing transfer 1_200, income 1_500.
        assertEquals(
            listOf(
                AccountDetailRecordKind.CASH_FLOW,
                AccountDetailRecordKind.BALANCE_UPDATE,
                AccountDetailRecordKind.TRANSFER,
                AccountDetailRecordKind.CASH_FLOW,
            ),
            snapshot.recentRecords.map { it.kind },
        )
        // Before → after pairs around each record of the viewed account (initial 1_000):
        // +500 income, -300 transfer out, 0 check (before == after), -100 expense.
        assertEquals(
            listOf(1_200L, 1_200L, 1_500L, 1_000L),
            snapshot.recentRecords.map { it.balanceBefore },
        )
        assertEquals(
            listOf(1_100L, 1_200L, 1_200L, 1_500L),
            snapshot.recentRecords.map { it.balanceAfter },
        )
        // The transfer amount stays viewer-relative (negative for the sending account).
        assertEquals(-300L, snapshot.recentRecords[2].amount)
    }
}
