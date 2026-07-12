package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBudgetViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `budget validates positive input and retains draft for retry after save failure`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel
        advanceUntilIdle()

        viewModel.openMonthlyBudgetEditor()
        viewModel.updateMonthlyBudgetInput("0")
        viewModel.saveMonthlyBudget()
        assertEquals(R.string.home_budget_positive_error, viewModel.uiState.value.monthlyBudgetInputErrorRes)
        val restoredError = fixture.recreateViewModel()
        assertEquals(R.string.home_budget_positive_error, restoredError.uiState.value.monthlyBudgetInputErrorRes)

        viewModel.updateMonthlyBudgetInput("123.45")
        val restored = fixture.recreateViewModel()
        assertTrue(restored.uiState.value.showMonthlyBudgetEditor)
        assertEquals("123.45", restored.uiState.value.monthlyBudgetInput)
        fixture.settings.failNextUpdate = true
        viewModel.saveMonthlyBudget()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMonthlyBudgetEditor)
        assertEquals("123.45", viewModel.uiState.value.monthlyBudgetInput)
        assertEquals(R.string.home_budget_save_failed, viewModel.uiState.value.monthlyBudgetSaveErrorRes)

        viewModel.retryMonthlyBudgetSave()
        viewModel.retryMonthlyBudgetSave()
        advanceUntilIdle()
        assertEquals(12_345L, fixture.settings.query().monthlyBudgetAmount)
        assertEquals(2, fixture.settings.updateCalls)
        assertFalse(viewModel.uiState.value.showMonthlyBudgetEditor)
    }

    @Test
    fun `failed close survives recreation and retry still closes instead of saving old input`() = runTest(dispatcher) {
        val fixture = fixture(PortableSettings(monthlyBudgetAmount = 50_000L))
        val viewModel = fixture.viewModel
        advanceUntilIdle()

        viewModel.openMonthlyBudgetEditor()
        fixture.settings.failNextUpdate = true
        viewModel.closeMonthlyBudget()
        advanceUntilIdle()
        assertEquals(R.string.home_budget_save_failed, viewModel.uiState.value.monthlyBudgetSaveErrorRes)

        val restored = fixture.recreateViewModel()
        assertEquals(R.string.home_budget_save_failed, restored.uiState.value.monthlyBudgetSaveErrorRes)
        restored.retryMonthlyBudgetSave()
        advanceUntilIdle()

        assertEquals(null, fixture.settings.query().monthlyBudgetAmount)
        assertEquals(listOf<Long?>(null, null), fixture.settings.updatedAmounts)
    }

    private suspend fun fixture(
        initial: PortableSettings = PortableSettings(),
    ): Fixture {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val settings = FailOncePortableSettingsRepository(initial)
        val clock = testClockProvider(now)
        val home = ObserveHomeDashboardUseCase(
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
        )
        val devicePreferences = InMemoryDevicePreferencesRepository()
        val savedStateHandle = SavedStateHandle()
        return Fixture(
            settings = settings,
            home = home,
            devicePreferences = devicePreferences,
            savedStateHandle = savedStateHandle,
            viewModel = HomeViewModel(
                observeHomeDashboardUseCase = home,
                devicePreferencesRepository = devicePreferences,
                portableSettingsRepository = settings,
                savedStateHandle = savedStateHandle,
            ),
        )
    }

    private data class Fixture(
        val settings: FailOncePortableSettingsRepository,
        val home: ObserveHomeDashboardUseCase,
        val devicePreferences: InMemoryDevicePreferencesRepository,
        val savedStateHandle: SavedStateHandle,
        val viewModel: HomeViewModel,
    ) {
        fun recreateViewModel() = HomeViewModel(
            observeHomeDashboardUseCase = home,
            devicePreferencesRepository = devicePreferences,
            portableSettingsRepository = settings,
            savedStateHandle = savedStateHandle,
        )
    }
}

private class FailOncePortableSettingsRepository(
    initial: PortableSettings,
) : PortableSettingsRepository {
    private val state = MutableStateFlow(initial)
    var failNextUpdate: Boolean = false
    var updateCalls: Int = 0
    val updatedAmounts = mutableListOf<Long?>()

    override fun observe(): Flow<PortableSettings> = state.asStateFlow()
    override suspend fun query(): PortableSettings = state.value
    override suspend fun updateCurrencySymbol(symbol: String) = update { copy(currencySymbol = symbol) }
    override suspend fun updateAmountColorMode(mode: AmountColorMode) = update { copy(amountColorMode = mode) }

    override suspend fun updateMonthlyBudgetAmount(amount: Long?) {
        updateCalls += 1
        updatedAmounts += amount
        if (failNextUpdate) {
            failNextUpdate = false
            error("write failed")
        }
        update { copy(monthlyBudgetAmount = amount) }
    }

    override suspend fun replace(settings: PortableSettings) {
        state.value = settings
    }

    private fun update(block: PortableSettings.() -> PortableSettings) {
        state.value = state.value.block()
    }
}
