package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.*
import com.shihuaidexianyu.money.domain.model.*
import com.shihuaidexianyu.money.domain.usecase.*
import com.shihuaidexianyu.money.ui.reminder.ReminderListViewModel
import java.time.ZoneId
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderListPendingSkipTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }
    @After fun tearDown() { Dispatchers.resetMain(); ArchTaskExecutor.getInstance().setDelegate(null) }

    @Test
    fun `skip terminal survives recreation wrong ack and double tap`() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.skipReminder(fixture.reminderId, fixture.dueAt)
        first.skipReminder(fixture.reminderId, fixture.dueAt)
        advanceUntilIdle()
        val pending = requireNotNull(first.uiState.value.pendingSkip)
        assertEquals(fixture.dueAt, pending.undoToken.skippedDueAt)
        assertEquals(fixture.dueAt + DAY, fixture.reminders.getReminderById(fixture.reminderId)?.nextDueAt)

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals(pending, recreated.uiState.value.pendingSkip)
        recreated.ackPendingSkip("wrong")
        assertEquals(pending, recreated.uiState.value.pendingSkip)
        recreated.ackPendingSkip(pending.token)
        assertNull(recreated.uiState.value.pendingSkip)
    }

    @Test
    fun `different reminders cannot concurrently overwrite pending undo`() = runTest(dispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.viewModel(SavedStateHandle())
        advanceUntilIdle()

        viewModel.skipReminder(fixture.reminderId, fixture.dueAt)
        viewModel.skipReminder(fixture.secondReminderId, fixture.dueAt)
        advanceUntilIdle()

        val firstPending = requireNotNull(viewModel.uiState.value.pendingSkip)
        assertEquals(fixture.reminderId, firstPending.undoToken.reminderId)
        assertEquals(fixture.dueAt + DAY, fixture.reminders.getReminderById(fixture.reminderId)?.nextDueAt)
        assertEquals(fixture.dueAt, fixture.reminders.getReminderById(fixture.secondReminderId)?.nextDueAt)

        viewModel.ackPendingSkip(firstPending.token)
        viewModel.skipReminder(fixture.secondReminderId, fixture.dueAt)
        advanceUntilIdle()
        assertEquals(fixture.secondReminderId, viewModel.uiState.value.pendingSkip?.undoToken?.reminderId)
        assertEquals(fixture.dueAt + DAY, fixture.reminders.getReminderById(fixture.secondReminderId)?.nextDueAt)
    }

    private class Fixture {
        val dueAt = 10L * DAY
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val transactions = InMemoryTransactionRepository()
        val reminderSettings = InMemoryAccountReminderSettingsRepository()
        val portable = InMemoryPortableSettingsRepository()
        val preferences = InMemoryDevicePreferencesRepository()
        val accountId = runBlocking { accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1)) }
        val reminderId = runBlocking {
            reminders.insertReminder(RecurringReminder(
                name = "账单", type = "manual", accountId = accountId, direction = "outflow", amount = 100,
                periodType = "custom_days", periodValue = 1, periodMonth = null, nextDueAt = dueAt,
                anchorDueAt = dueAt, createdAt = 1, updatedAt = 2,
            ))
        }
        val secondReminderId = runBlocking {
            reminders.insertReminder(RecurringReminder(
                name = "第二账单", type = "manual", accountId = accountId, direction = "outflow", amount = 200,
                periodType = "custom_days", periodValue = 1, periodMonth = null, nextDueAt = dueAt,
                anchorDueAt = dueAt, createdAt = 1, updatedAt = 2,
            ))
        }
        private val clock = testClockProvider(20L * DAY)
        private val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { ZoneId.of("UTC") }
        private val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        private val dashboard = ObserveHomeDashboardUseCase(
            reminderSettings, accounts, reminders, portable, transactions,
            CalculateCurrentBalanceUseCase(accounts, transactions, clock),
            CalculateAccountBalancesUseCase(transactions, clock), clock, zone,
        )
        fun viewModel(handle: SavedStateHandle) = ReminderListViewModel(
            reminders, DeleteReminderUseCase(accounts, reminders), SkipReminderUseCase(accounts, reminders, clock, zone),
            UndoSkipReminderUseCase(reminders, clock, com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester),
            dashboard, clock, zone, preferences, handle,
        )
    }

    private companion object { const val DAY = 86_400_000L }
}
