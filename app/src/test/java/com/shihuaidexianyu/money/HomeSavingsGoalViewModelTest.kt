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
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSavingsGoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `goal validates positive input and retains draft for retry after save failure`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel
        advanceUntilIdle()

        viewModel.openSavingsGoalEditor()
        viewModel.updateSavingsGoalInput("0")
        viewModel.saveSavingsGoal()
        assertEquals(R.string.goal_amount_invalid, viewModel.uiState.value.savingsGoalInputErrorRes)
        val restoredError = fixture.recreateViewModel()
        assertEquals(R.string.goal_amount_invalid, restoredError.uiState.value.savingsGoalInputErrorRes)

        viewModel.updateSavingsGoalInput("123.45")
        val restored = fixture.recreateViewModel()
        assertTrue(restored.uiState.value.showSavingsGoalEditor)
        assertEquals("123.45", restored.uiState.value.savingsGoalInput)
        fixture.goals.failNextUpsert = true
        viewModel.saveSavingsGoal()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showSavingsGoalEditor)
        assertEquals("123.45", viewModel.uiState.value.savingsGoalInput)
        assertEquals(R.string.home_savings_goal_save_failed, viewModel.uiState.value.savingsGoalSaveErrorRes)

        viewModel.retrySavingsGoalSave()
        viewModel.retrySavingsGoalSave()
        advanceUntilIdle()
        assertEquals(12_345L, fixture.goals.query()?.targetAmount)
        assertEquals(2, fixture.goals.upsertCalls)
        assertFalse(viewModel.uiState.value.showSavingsGoalEditor)
    }

    @Test
    fun `failed clear survives recreation and retry still clears instead of saving old input`() = runBlocking {
        // The dashboard projection uses flowOn(Dispatchers.Default), so awaiting real emissions
        // needs an unconfined Main and a real-time first{} — the HomeRecentRecordsViewModelTest
        // pattern; advanceUntilIdle() alone races the background projection thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val fixture = fixture(seedGoalAmount = 50_000L)
        val viewModel = fixture.viewModel
        withTimeout(5_000L) {
            viewModel.uiState.first { it.savingsGoalProgress != null }
        }

        viewModel.openSavingsGoalEditor()
        assertEquals("500", viewModel.uiState.value.savingsGoalInput)
        fixture.goals.failNextClear = true
        viewModel.clearSavingsGoal()
        assertEquals(R.string.home_savings_goal_save_failed, viewModel.uiState.value.savingsGoalSaveErrorRes)

        val restored = fixture.recreateViewModel()
        assertEquals(R.string.home_savings_goal_save_failed, restored.uiState.value.savingsGoalSaveErrorRes)
        restored.retrySavingsGoalSave()
        withTimeout(5_000L) {
            restored.uiState.first { !it.showSavingsGoalEditor }
        }

        assertNull(fixture.goals.query())
    }

    private suspend fun fixture(
        seedGoalAmount: Long? = null,
    ): Fixture {
        val now = Instant.parse("2026-02-15T10:00:00Z").toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val settings = InMemoryPortableSettingsRepository(PortableSettings())
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
        val goals = FailOnceSavingsGoalRepository(InMemorySavingsGoalRepository())
        if (seedGoalAmount != null) {
            goals.upsert(seedGoalAmount, now)
        }
        val savingsGoal = ObserveSavingsGoalUseCase(
            accountRepository = accounts,
            savingsGoalRepository = goals,
            transactionRepository = ledger,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger, clock),
        )
        val upsertSavingsGoal = UpsertSavingsGoalUseCase(goals, clock)
        val clearSavingsGoal = ClearSavingsGoalUseCase(goals)
        return Fixture(
            goals = goals,
            home = home,
            savingsGoal = savingsGoal,
            upsertSavingsGoal = upsertSavingsGoal,
            clearSavingsGoal = clearSavingsGoal,
            devicePreferences = devicePreferences,
            settings = settings,
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
        val goals: FailOnceSavingsGoalRepository,
        val home: ObserveHomeDashboardUseCase,
        val savingsGoal: ObserveSavingsGoalUseCase,
        val upsertSavingsGoal: UpsertSavingsGoalUseCase,
        val clearSavingsGoal: ClearSavingsGoalUseCase,
        val devicePreferences: InMemoryDevicePreferencesRepository,
        val settings: InMemoryPortableSettingsRepository,
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

private class FailOnceSavingsGoalRepository(
    private val delegate: InMemorySavingsGoalRepository,
) : SavingsGoalRepository by delegate {
    var failNextUpsert: Boolean = false
    var failNextClear: Boolean = false
    var upsertCalls: Int = 0

    override suspend fun upsert(targetAmount: Long, now: Long) {
        upsertCalls += 1
        if (failNextUpsert) {
            failNextUpsert = false
            error("write failed")
        }
        delegate.upsert(targetAmount, now)
    }

    override suspend fun clear() {
        if (failNextClear) {
            failNextClear = false
            error("write failed")
        }
        delegate.clear()
    }
}
