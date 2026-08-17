package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BudgetPeriod
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
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
import kotlinx.coroutines.flow.first
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

        viewModel.openBudgetEditor()
        viewModel.updateBudgetInput("0")
        viewModel.saveBudget()
        assertEquals(R.string.home_budget_positive_error, viewModel.uiState.value.budgetInputErrorRes)
        val restoredError = fixture.recreateViewModel()
        assertEquals(R.string.home_budget_positive_error, restoredError.uiState.value.budgetInputErrorRes)

        viewModel.updateBudgetInput("123.45")
        val restored = fixture.recreateViewModel()
        assertTrue(restored.uiState.value.showBudgetEditor)
        assertEquals("123.45", restored.uiState.value.budgetInput)
        fixture.settings.failNextUpdate = true
        viewModel.saveBudget()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showBudgetEditor)
        assertEquals("123.45", viewModel.uiState.value.budgetInput)
        assertEquals(R.string.home_budget_save_failed, viewModel.uiState.value.budgetSaveErrorRes)

        viewModel.retryBudgetSave()
        viewModel.retryBudgetSave()
        advanceUntilIdle()
        assertEquals(12_345L, fixture.settings.query().budgetAmount)
        assertEquals(2, fixture.settings.updateCalls)
        assertFalse(viewModel.uiState.value.showBudgetEditor)
    }

    @Test
    fun `failed close survives recreation and retry still closes instead of saving old input`() = runTest(dispatcher) {
        val fixture = fixture(PortableSettings(budgetAmount = 50_000L))
        val viewModel = fixture.viewModel
        advanceUntilIdle()

        viewModel.openBudgetEditor()
        fixture.settings.failNextUpdate = true
        viewModel.closeBudget()
        advanceUntilIdle()
        assertEquals(R.string.home_budget_save_failed, viewModel.uiState.value.budgetSaveErrorRes)

        val restored = fixture.recreateViewModel()
        assertEquals(R.string.home_budget_save_failed, restored.uiState.value.budgetSaveErrorRes)
        restored.retryBudgetSave()
        advanceUntilIdle()

        assertEquals(null, fixture.settings.query().budgetAmount)
        assertEquals(listOf<Long?>(null, null), fixture.settings.updatedAmounts)
    }

    @Test
    fun `editor seeds saved period and saving persists amount with the picked period`() = runTest(dispatcher) {
        val fixture = fixture(
            PortableSettings(budgetAmount = 50_000L, budgetPeriod = BudgetPeriod.YEARLY),
        )
        val viewModel = fixture.viewModel
        // The dashboard observes on a real background dispatcher, so wait until the saved
        // settings have actually landed in the UI state before opening the editor.
        viewModel.uiState.first { it.settings.budgetPeriod == BudgetPeriod.YEARLY }

        viewModel.openBudgetEditor()
        assertEquals(BudgetPeriod.YEARLY, viewModel.uiState.value.budgetEditorPeriod)
        assertEquals("500", viewModel.uiState.value.budgetInput)

        // Switching back to monthly survives recreation, then saving writes both fields.
        viewModel.updateBudgetEditorPeriod(BudgetPeriod.MONTHLY)
        val restored = fixture.recreateViewModel()
        assertEquals(BudgetPeriod.MONTHLY, restored.uiState.value.budgetEditorPeriod)
        restored.saveBudget()
        advanceUntilIdle()

        val saved = fixture.settings.query()
        assertEquals(50_000L, saved.budgetAmount)
        assertEquals(BudgetPeriod.MONTHLY, saved.budgetPeriod)
    }

    @Test
    fun `failed save retries with the period picked before recreation`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel
        advanceUntilIdle()

        viewModel.openBudgetEditor()
        viewModel.updateBudgetInput("200")
        viewModel.updateBudgetEditorPeriod(BudgetPeriod.YEARLY)
        fixture.settings.failNextUpdate = true
        viewModel.saveBudget()
        advanceUntilIdle()
        assertEquals(R.string.home_budget_save_failed, viewModel.uiState.value.budgetSaveErrorRes)

        val restored = fixture.recreateViewModel()
        restored.retryBudgetSave()
        advanceUntilIdle()

        val saved = fixture.settings.query()
        assertEquals(20_000L, saved.budgetAmount)
        assertEquals(BudgetPeriod.YEARLY, saved.budgetPeriod)
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
        val savingsGoalRepository = InMemorySavingsGoalRepository()
        val savingsGoal = ObserveSavingsGoalUseCase(
            accountRepository = accounts,
            savingsGoalRepository = savingsGoalRepository,
            transactionRepository = ledger,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger, clock),
        )
        val upsertSavingsGoal = UpsertSavingsGoalUseCase(savingsGoalRepository, clock)
        val clearSavingsGoal = ClearSavingsGoalUseCase(savingsGoalRepository)
        return Fixture(
            settings = settings,
            home = home,
            savingsGoal = savingsGoal,
            upsertSavingsGoal = upsertSavingsGoal,
            clearSavingsGoal = clearSavingsGoal,
            devicePreferences = devicePreferences,
            savedStateHandle = savedStateHandle,
            viewModel = HomeViewModel(
                observeHomeDashboardUseCase = home,
                observeSavingsGoalUseCase = savingsGoal,
                upsertSavingsGoalUseCase = upsertSavingsGoal,
                clearSavingsGoalUseCase = clearSavingsGoal,
                devicePreferencesRepository = devicePreferences,
                portableSettingsRepository = settings,
                savedStateHandle = savedStateHandle,
            ),
        )
    }

    private data class Fixture(
        val settings: FailOncePortableSettingsRepository,
        val home: ObserveHomeDashboardUseCase,
        val savingsGoal: ObserveSavingsGoalUseCase,
        val upsertSavingsGoal: UpsertSavingsGoalUseCase,
        val clearSavingsGoal: ClearSavingsGoalUseCase,
        val devicePreferences: InMemoryDevicePreferencesRepository,
        val savedStateHandle: SavedStateHandle,
        val viewModel: HomeViewModel,
    ) {
        fun recreateViewModel() = HomeViewModel(
            observeHomeDashboardUseCase = home,
            observeSavingsGoalUseCase = savingsGoal,
            upsertSavingsGoalUseCase = upsertSavingsGoal,
            clearSavingsGoalUseCase = clearSavingsGoal,
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

    override suspend fun updateBudget(amount: Long?, period: BudgetPeriod) {
        updateCalls += 1
        updatedAmounts += amount
        if (failNextUpdate) {
            failNextUpdate = false
            error("write failed")
        }
        update { copy(budgetAmount = amount, budgetPeriod = period) }
    }

    override suspend fun replace(settings: PortableSettings) {
        state.value = settings
    }

    private fun update(block: PortableSettings.() -> PortableSettings) {
        state.value = state.value.block()
    }
}
