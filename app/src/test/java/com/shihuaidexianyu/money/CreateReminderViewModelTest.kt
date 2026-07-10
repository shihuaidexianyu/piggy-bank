package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.ui.reminder.CreateReminderViewModel
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateReminderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `reminder picker includes hidden open accounts and excludes closed accounts`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val visibleId = accountRepository.createAccount(
            Account(name = "显示", initialBalance = 100L, createdAt = 1L, displayOrder = 0),
        )
        val hiddenId = accountRepository.createAccount(
            Account(name = "隐藏", initialBalance = 200L, createdAt = 2L, isHidden = true, displayOrder = 1),
        )
        val closedId = accountRepository.createAccount(
            Account(name = "关闭", initialBalance = 0L, createdAt = 3L, displayOrder = 2),
        )
        accountRepository.closeAccount(closedId, 4L)
        val viewModel = CreateReminderViewModel(
            accountRepository = accountRepository,
            createReminderUseCase = CreateReminderUseCase(
                accountRepository,
                reminderRepository,
                testClockProvider(1_000L),
                { java.time.ZoneId.of("Asia/Shanghai") },
            ),
            savedStateHandle = SavedStateHandle(),
            clockProvider = testClockProvider(1_000L),
            zoneIdProvider = { java.time.ZoneId.of("Asia/Shanghai") },
        )

        advanceUntilIdle()

        assertEquals(listOf(visibleId, hiddenId), viewModel.uiState.value.accounts.map { it.id })
        assertEquals(visibleId, viewModel.uiState.value.selectedAccountId)
    }

    @Test
    fun `anchor date time draft survives recreation and drives monthly anchor`() = runTest(dispatcher) {
        val now = java.time.Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { java.time.ZoneId.of("UTC") }
        val clock = testClockProvider(now)
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        accounts.createAccount(Account(name = "钱包", initialBalance = 0, createdAt = 1))
        val handle = SavedStateHandle()

        fun create() = CreateReminderViewModel(
            accountRepository = accounts,
            createReminderUseCase = CreateReminderUseCase(accounts, reminders, clock, zone),
            savedStateHandle = handle,
            clockProvider = clock,
            zoneIdProvider = zone,
        )

        val first = create()
        advanceUntilIdle()
        first.updateName("房租")
        first.updateAmount("100.00")
        first.updatePeriodType(ReminderPeriodType.MONTHLY)
        first.updateAnchorDate("2025-02-03")
        first.updateAnchorTime("09:45")

        val recreated = create()
        advanceUntilIdle()
        assertEquals("2025-02-03", recreated.uiState.value.anchorDateText)
        assertEquals("09:45", recreated.uiState.value.anchorTimeText)
        assertEquals("房租", recreated.uiState.value.name)

        recreated.save()
        advanceUntilIdle()

        val stored = reminders.queryAll().single()
        assertEquals(java.time.Instant.parse("2025-02-03T09:45:00Z").toEpochMilli(), stored.anchorDueAt)
        assertEquals(3, stored.periodValue)
        assertEquals(stored.anchorDueAt, stored.nextDueAt)
    }

    @Test
    fun `past anchor is reported on the anchor fields without writing`() = runTest(dispatcher) {
        val now = java.time.Instant.parse("2025-01-02T00:00:00Z").toEpochMilli()
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { java.time.ZoneId.of("UTC") }
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        accounts.createAccount(Account(name = "钱包", initialBalance = 0, createdAt = 1))
        val viewModel = CreateReminderViewModel(
            accountRepository = accounts,
            createReminderUseCase = CreateReminderUseCase(accounts, reminders, { now }, zone),
            savedStateHandle = SavedStateHandle(),
            clockProvider = { now },
            zoneIdProvider = zone,
        )
        advanceUntilIdle()
        viewModel.updateName("账单")
        viewModel.updateAmount("10")
        viewModel.updateAnchorDate("2025-01-01")
        viewModel.updateAnchorTime("12:00")

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.anchorError?.contains("晚于当前时间") == true)
        assertTrue(reminders.queryAll().isEmpty())
    }
}
