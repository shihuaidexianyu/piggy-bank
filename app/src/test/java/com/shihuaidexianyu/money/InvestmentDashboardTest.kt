package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class InvestmentDashboardTest {
    private val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()

    @Test
    fun `reconciliation deltas count as investment pnl only on investment accounts`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val fundingId = accounts.createAccount(
            Account(name = "现金", initialBalance = 10_000L, createdAt = 1L),
        )
        val investmentId = accounts.createAccount(
            Account(name = "基金", initialBalance = 50_000L, createdAt = 1L, kind = AccountKind.INVESTMENT),
        )
        val range = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, now)
        // A correction on the funding account and a market gain on the fund, both this month.
        insertReconciliation(ledger, fundingId, delta = -300L, occurredAt = range.startInclusive + 1L)
        insertReconciliation(ledger, investmentId, delta = 820L, occurredAt = range.startInclusive + 2L)

        val snapshot = dashboard(accounts, ledger).first()

        assertTrue(snapshot.hasInvestmentAccounts)
        assertEquals(820L, snapshot.periodInvestmentPnl)
        assertEquals(50_820L, snapshot.investmentAssets)
        assertEquals(10_000L - 300L + 50_820L, snapshot.totalAssets)
        // The generic reconciliation aggregate still reports both deltas — the split is a
        // read-time interpretation, not a change to ledger arithmetic.
        assertEquals(820L, snapshot.periodBreakdown.reconciliationIncrease)
        assertEquals(300L, snapshot.periodBreakdown.reconciliationDecrease)
    }

    @Test
    fun `deltas outside the selected period do not count`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val investmentId = accounts.createAccount(
            Account(name = "基金", initialBalance = 50_000L, createdAt = 1L, kind = AccountKind.INVESTMENT),
        )
        val range = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, now)
        insertReconciliation(ledger, investmentId, delta = 700L, occurredAt = range.startInclusive - 1L)
        insertReconciliation(ledger, investmentId, delta = 120L, occurredAt = range.startInclusive + 1L)

        val snapshot = dashboard(accounts, ledger).first()

        assertEquals(120L, snapshot.periodInvestmentPnl)
        // Assets are cumulative, so the older gain still shows in the balance split.
        assertEquals(50_820L, snapshot.investmentAssets)
    }

    @Test
    fun `dashboards without investment accounts report nothing investment-related`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val fundingId = accounts.createAccount(
            Account(name = "现金", initialBalance = 10_000L, createdAt = 1L),
        )
        val range = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, now)
        insertReconciliation(ledger, fundingId, delta = 100L, occurredAt = range.startInclusive + 1L)

        val snapshot = dashboard(accounts, ledger).first()

        assertFalse(snapshot.hasInvestmentAccounts)
        assertEquals(0L, snapshot.periodInvestmentPnl)
        assertEquals(0L, snapshot.investmentAssets)
    }

    @Test
    fun `reclassifying an account reinterprets its existing history`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(
            Account(name = "基金", initialBalance = 50_000L, createdAt = 1L),
        )
        val range = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, now)
        insertReconciliation(ledger, accountId, delta = 820L, occurredAt = range.startInclusive + 1L)

        val before = dashboard(accounts, ledger).first()
        assertEquals(0L, before.periodInvestmentPnl)

        // Flip the account kind — the already-recorded delta becomes investment P&L.
        val account = requireNotNull(accounts.getAccountById(accountId))
        accounts.updateAccount(account.copy(kind = AccountKind.INVESTMENT))

        val after = dashboard(accounts, ledger).first()
        assertEquals(820L, after.periodInvestmentPnl)
        assertEquals(50_820L, after.investmentAssets)
    }

    private suspend fun insertReconciliation(
        ledger: InMemoryTransactionRepository,
        accountId: Long,
        delta: Long,
        occurredAt: Long,
    ) {
        ledger.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 0L,
                systemBalanceBeforeUpdate = 0L,
                delta = delta,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
    }

    private fun dashboard(
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
    ): kotlinx.coroutines.flow.Flow<com.shihuaidexianyu.money.domain.usecase.HomeDashboardSnapshot> {
        val clock = testClockProvider(now)
        return ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            portableSettingsRepository = InMemoryPortableSettingsRepository(PortableSettings()),
            transactionRepository = ledger,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, ledger, clock),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger, clock),
            clockProvider = clock,
            zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
        ).invoke(MutableStateFlow(DashboardPeriod.MONTH))
    }
}
