package com.shihuaidexianyu.money

import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
import com.shihuaidexianyu.money.ui.settings.SavingsGoalEffect
import com.shihuaidexianyu.money.ui.settings.SavingsGoalViewModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsGoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `null goal upserts edits with single flight and clears`() = runTest(dispatcher) {
        val repository = InMemorySavingsGoalRepository()
        val clock = CountingClock(100L)
        val upsert = UpsertSavingsGoalUseCase(repository, clock)
        val clear = ClearSavingsGoalUseCase(repository)
        val createViewModel = { SavingsGoalViewModel(repository, upsert, clear) }

        val initial = createViewModel()
        advanceUntilIdle()
        assertFalse(initial.uiState.value.isLoading)
        assertFalse(initial.uiState.value.hasGoal)
        initial.updateAmount("123.45")
        initial.effectFlow.test {
            initial.save()
            advanceUntilIdle()
            assertEquals(SavingsGoalEffect.Saved, awaitItem())
        }
        assertEquals(12_345L, repository.query()?.targetAmount)
        assertEquals(1, clock.calls)

        val edit = createViewModel()
        advanceUntilIdle()
        assertTrue(edit.uiState.value.hasGoal)
        assertEquals("123.45", edit.uiState.value.amountText)
        edit.updateAmount("200")
        edit.effectFlow.test {
            edit.save()
            edit.save()
            advanceUntilIdle()
            assertEquals(SavingsGoalEffect.Saved, awaitItem())
            expectNoEvents()
        }
        assertEquals(20_000L, repository.query()?.targetAmount)
        assertEquals(2, clock.calls)

        val remove = createViewModel()
        advanceUntilIdle()
        remove.showClearConfirm()
        remove.effectFlow.test {
            remove.clear()
            advanceUntilIdle()
            assertEquals(SavingsGoalEffect.Cleared, awaitItem())
        }
        assertNull(repository.query())
    }

    private class CountingClock(private val now: Long) : ClockProvider {
        var calls: Int = 0
            private set

        override fun nowMillis(): Long {
            calls += 1
            return now
        }
    }
}
