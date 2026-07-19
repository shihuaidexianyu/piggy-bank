package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRecentRecordsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `recent records map account names signed amounts and transfer paths`() = runBlocking {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val cashId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val bankId = accounts.createAccount(Account(name = "银行", initialBalance = 0L, createdAt = 1L))
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = cashId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 12_345,
                note = "工资",
                occurredAt = now - 3_000,
                createdAt = now - 3_000,
                updatedAt = now - 3_000,
                operationId = testOperationId(),
            ),
        )
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = bankId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500,
                note = "午饭",
                occurredAt = now - 2_000,
                createdAt = now - 2_000,
                updatedAt = now - 2_000,
                operationId = testOperationId(),
            ),
        )
        ledger.insertTransferRecord(
            TransferRecord(
                fromAccountId = cashId,
                toAccountId = bankId,
                amount = 1_000,
                note = "",
                occurredAt = now - 1_000,
                createdAt = now - 1_000,
                updatedAt = now - 1_000,
                operationId = testOperationId(),
            ),
        )
        val viewModel = fixtureViewModel(
            accounts = accounts,
            ledger = ledger,
            savingsGoalRepository = InMemorySavingsGoalRepository(),
            now = now,
        )

        val state = withTimeout(5_000L) {
            viewModel.uiState.first { it.hasCommittedContent || it.errorMessageRes != null }
        }
        assertEquals(null, state.errorMessageRes)
        val records = state.recentRecords
        assertEquals(3, records.size)
        assertEquals(HistoryRecordKind.TRANSFER, records[0].kind)
        assertEquals("账户间转移", records[0].title)
        assertEquals("现金 → 银行", records[0].subtitle)
        assertEquals(1_000L, records[0].amount)
        assertEquals(now - 1_000, records[0].occurredAt)
        assertEquals(HistoryRecordKind.CASH_FLOW, records[1].kind)
        assertEquals("午饭", records[1].title)
        assertEquals("银行", records[1].subtitle)
        assertEquals(-500L, records[1].amount)
        assertEquals(HistoryRecordKind.CASH_FLOW, records[2].kind)
        assertEquals("工资", records[2].title)
        assertEquals("现金", records[2].subtitle)
        assertEquals(12_345L, records[2].amount)
        assertNull(state.savingsGoalProgress)
    }

    @Test
    fun `savings goal progress is combined into home state`() = runBlocking {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 30_000L, createdAt = 1L))
        val savingsGoalRepository = InMemorySavingsGoalRepository()
        savingsGoalRepository.upsert(targetAmount = 100_000L, now = now)
        val viewModel = fixtureViewModel(
            accounts = accounts,
            ledger = ledger,
            savingsGoalRepository = savingsGoalRepository,
            now = now,
        )

        val progress = withTimeout(5_000L) {
            viewModel.uiState.first { it.savingsGoalProgress != null || it.errorMessageRes != null }
        }.savingsGoalProgress
        assertEquals(100_000L, progress?.targetAmount)
        assertEquals(30_000L, progress?.currentAmount)
        assertEquals(false, progress?.isAchieved)
    }

    private fun fixtureViewModel(
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
        savingsGoalRepository: InMemorySavingsGoalRepository,
        now: Long,
    ): HomeViewModel {
        val clock = testClockProvider(now)
        val balances = CalculateAccountBalancesUseCase(ledger, clock)
        val home = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            portableSettingsRepository = InMemoryPortableSettingsRepository(PortableSettings()),
            transactionRepository = ledger,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, ledger, clock),
            calculateAccountBalancesUseCase = balances,
            clockProvider = clock,
            zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
        )
        return HomeViewModel(
            observeHomeDashboardUseCase = home,
            observeSavingsGoalUseCase = ObserveSavingsGoalUseCase(
                accountRepository = accounts,
                savingsGoalRepository = savingsGoalRepository,
                transactionRepository = ledger,
                calculateAccountBalancesUseCase = balances,
            ),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
            portableSettingsRepository = InMemoryPortableSettingsRepository(PortableSettings()),
            savedStateHandle = SavedStateHandle(),
        )
    }
}
