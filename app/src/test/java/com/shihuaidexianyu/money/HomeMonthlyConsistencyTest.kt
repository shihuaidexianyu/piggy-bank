package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.home.toAsyncContent
import app.cash.turbine.test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HomeMonthlyConsistencyTest {
    @Test
    fun `home contract has no selectable period type or field`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("com.shihuaidexianyu.money.domain.model.HomePeriod")
        }
        val forbidden = setOf("period", "homePeriod", "selectedPeriod")
        assertEquals(
            emptySet(),
            com.shihuaidexianyu.money.ui.home.HomeUiState::class.java.declaredFields
                .map { it.name }
                .filterTo(mutableSetOf()) { it in forbidden },
        )
    }

    @Test
    fun `closed-only ledger remains data and preserves legacy nonzero net assets`() = runBlocking {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(
            Account(name = "遗留关闭账户", initialBalance = -5_000L, createdAt = 1L),
        )
        accounts.closeAccount(accountId, now - 1L)
        val snapshot = homeUseCase(
            now = now,
            accounts = accounts,
            ledger = ledger,
            settings = InMemoryPortableSettingsRepository(),
            timeSignal = MutableStateFlow(now),
        ).first()

        assertEquals(true, snapshot.hasAnyAccounts)
        assertEquals(1, snapshot.allAccountCount)
        assertEquals(emptyList(), snapshot.openAccounts)
        assertEquals(-5_000L, snapshot.totalAssets)
        assertIs<AsyncContent.Data<HomeUiState>>(
            HomeUiState(
                isLoading = false,
                hasCommittedContent = true,
                hasAnyAccounts = true,
                allAccountCount = 1,
                totalAssets = -5_000L,
            ).toAsyncContent(),
        )
        Unit
    }

    @Test
    fun `same collector resets monthly cash flow and budget when time signal enters next month`() = runBlocking {
        val januaryNow = Instant.parse("2026-01-15T10:00:00Z").toEpochMilli()
        val februaryNow = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val january = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, januaryNow)
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val settings = InMemoryPortableSettingsRepository(PortableSettings(monthlyBudgetAmount = 1_000L))
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        insertCash(ledger, accountId, CashFlowDirection.OUTFLOW, 400L, january.startInclusive, "一月支出")
        val timeSignal = MutableStateFlow(januaryNow)

        homeUseCase(januaryNow, accounts, ledger, settings, timeSignal).test {
            val januarySnapshot = awaitItem()
            assertEquals(400L, januarySnapshot.periodBreakdown.cashOutflow)
            assertEquals(400L, requireNotNull(januarySnapshot.monthlyBudget).spentAmount)

            timeSignal.value = februaryNow
            val februarySnapshot = awaitItem()
            assertEquals(0L, februarySnapshot.periodBreakdown.cashInflow)
            assertEquals(0L, februarySnapshot.periodBreakdown.cashOutflow)
            assertEquals(0L, requireNotNull(februarySnapshot.monthlyBudget).spentAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `home history and budget share one half-open active cash-flow scope`() = runBlocking {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val range = TimeRangeCalculator.currentMonthRange(ZoneOffset.UTC, now)
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val settings = InMemoryPortableSettingsRepository(
            PortableSettings(monthlyBudgetAmount = 1_000L),
        )
        val firstId = accounts.createAccount(
            Account(name = "现金", initialBalance = -10_000L, createdAt = range.startInclusive - 1L),
        )
        val secondId = accounts.createAccount(
            Account(name = "银行卡", initialBalance = 1_000L, createdAt = range.startInclusive - 1L),
        )

        insertCash(ledger, firstId, CashFlowDirection.INFLOW, 2_000L, range.startInclusive, "起点收入")
        insertCash(ledger, firstId, CashFlowDirection.OUTFLOW, 400L, range.startInclusive + 1L, "本月支出")
        insertCash(ledger, firstId, CashFlowDirection.OUTFLOW, 900L, range.endExclusive, "下月支出")
        val deletedId = insertCash(
            ledger,
            firstId,
            CashFlowDirection.OUTFLOW,
            700L,
            range.startInclusive + 2L,
            "已删支出",
        )
        ledger.softDeleteCurrentCashFlowRecord(deletedId, range.startInclusive + 3L)
        ledger.insertTransferRecord(
            TransferRecord(
                fromAccountId = firstId,
                toAccountId = secondId,
                amount = 300L,
                note = "转账",
                occurredAt = range.startInclusive + 4L,
                createdAt = range.startInclusive + 4L,
                updatedAt = range.startInclusive + 4L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = firstId,
                delta = 200L,
                occurredAt = range.startInclusive + 5L,
                createdAt = range.startInclusive + 5L,
                updatedAt = range.startInclusive + 5L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = firstId,
                actualBalance = -8_100L,
                systemBalanceBeforeUpdate = -8_200L,
                delta = 100L,
                occurredAt = range.startInclusive + 6L,
                createdAt = range.startInclusive + 6L,
                updatedAt = range.startInclusive + 6L,
                operationId = testOperationId(),
            ),
        )

        val clock = testClockProvider(now)
        val zone = testZoneIdProvider(ZoneOffset.UTC)
        val balances = CalculateAccountBalancesUseCase(ledger, clock)
        val home = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            portableSettingsRepository = settings,
            transactionRepository = ledger,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, ledger, clock),
            calculateAccountBalancesUseCase = balances,
            clockProvider = clock,
            zoneIdProvider = zone,
        ).invoke().first()
        val history = ledger.queryHistoryRecords(
            filters = HistoryRecordFilters(
                dateStartAt = range.startInclusive,
                dateEndAt = range.endExclusive,
            ),
            cursor = null,
            limit = 100,
        )
        val historyCash = history.filter { it.type == HistoryRecordType.CASH_FLOW }
        val historyIncome = historyCash.filter { it.amount > 0L }.sumOf { it.amount }
        val historyExpense = historyCash.filter { it.amount < 0L }.sumOf { -it.amount }

        assertEquals(2_000L, historyIncome)
        assertEquals(400L, historyExpense)
        assertEquals(historyIncome, home.periodBreakdown.cashInflow)
        assertEquals(historyExpense, home.periodBreakdown.cashOutflow)
        assertEquals(historyIncome - historyExpense, home.periodBreakdown.cashNet)
        assertEquals(400L, requireNotNull(home.monthlyBudget).spentAmount)
        assertEquals(-7_100L, home.totalAssets)
    }

    private suspend fun insertCash(
        ledger: InMemoryTransactionRepository,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        occurredAt: Long,
        note: String,
    ): Long {
        return ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        ).recordId
    }

    private fun homeUseCase(
        now: Long,
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
        settings: InMemoryPortableSettingsRepository,
        timeSignal: MutableStateFlow<Long>,
    ): kotlinx.coroutines.flow.Flow<com.shihuaidexianyu.money.domain.usecase.HomeDashboardSnapshot> {
        val clock = testClockProvider(now)
        return ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            portableSettingsRepository = settings,
            transactionRepository = ledger,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, ledger, clock),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger, clock),
            clockProvider = clock,
            zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
            timeSignal = timeSignal,
        ).invoke()
    }
}
