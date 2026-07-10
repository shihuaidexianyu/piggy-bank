package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.ui.reminder.EditReminderViewModel
import com.shihuaidexianyu.money.ui.reminder.EditReminderEffect
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditReminderViewModelTest {
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
    fun `edited anchor draft survives recreation and is saved`() = runTest(dispatcher) {
        val now = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { ZoneId.of("UTC") }
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val accountId = accounts.createAccount(Account(name = "钱包", initialBalance = 0, createdAt = 1))
        val originalAnchor = Instant.parse("2025-02-01T08:00:00Z").toEpochMilli()
        val reminderId = reminders.insertReminder(
            RecurringReminder(
                name = "房租",
                type = "manual",
                accountId = accountId,
                direction = "outflow",
                amount = 10_000,
                periodType = "monthly",
                periodValue = 1,
                periodMonth = null,
                nextDueAt = originalAnchor,
                anchorDueAt = originalAnchor,
                isEnabled = false,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val handle = SavedStateHandle()

        fun create() = EditReminderViewModel(
            reminderId = reminderId,
            accountRepository = accounts,
            reminderRepository = reminders,
            updateReminderUseCase = UpdateReminderUseCase(accounts, reminders, { now }, zone),
            savedStateHandle = handle,
            zoneIdProvider = zone,
        )

        val first = create()
        advanceUntilIdle()
        first.updatePeriodType(ReminderPeriodType.YEARLY)
        first.updateAnchorDate("2025-03-14")
        first.updateAnchorTime("18:20")

        val recreated = create()
        advanceUntilIdle()
        assertEquals("2025-03-14", recreated.uiState.value.anchorDateText)
        assertEquals("18:20", recreated.uiState.value.anchorTimeText)

        recreated.updateEnabled(true)
        val savedEffect = async(start = CoroutineStart.UNDISPATCHED) { recreated.effectFlow.first() }
        recreated.save()
        advanceUntilIdle()

        val stored = reminders.getReminderById(reminderId)!!
        assertEquals(Instant.parse("2025-03-14T18:20:00Z").toEpochMilli(), stored.anchorDueAt)
        assertEquals(14, stored.periodValue)
        assertEquals(3, stored.periodMonth)
        assertTrue((savedEffect.await() as EditReminderEffect.Saved).shouldRequestNotificationPermission)
    }

    @Test
    fun `name only edit preserves exact legacy anchor schedule and dedupe after timezone change`() = runTest(dispatcher) {
        val now = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val shanghai = ZoneId.of("Asia/Shanghai")
        val newYork = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { ZoneId.of("America/New_York") }
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val accountId = accounts.createAccount(Account(name = "钱包", initialBalance = 0, createdAt = 1))
        val anchor = java.time.ZonedDateTime.of(2025, 1, 31, 0, 30, 45, 123_000_000, shanghai)
            .toInstant().toEpochMilli()
        val reminderId = reminders.insertReminder(
            RecurringReminder(
                name = "旧提醒",
                type = "manual",
                accountId = accountId,
                direction = "outflow",
                amount = 10_000,
                periodType = "monthly",
                periodValue = 31,
                periodMonth = null,
                nextDueAt = anchor,
                anchorDueAt = anchor,
                lastNotifiedDueAt = anchor,
                createdAt = 1,
                updatedAt = 50,
            ),
        )
        val viewModel = EditReminderViewModel(
            reminderId = reminderId,
            accountRepository = accounts,
            reminderRepository = reminders,
            updateReminderUseCase = UpdateReminderUseCase(accounts, reminders, { now }, newYork),
            savedStateHandle = SavedStateHandle(),
            zoneIdProvider = newYork,
        )
        advanceUntilIdle()

        viewModel.updateName("只改名称")
        viewModel.save()
        advanceUntilIdle()

        val stored = reminders.getReminderById(reminderId)!!
        assertEquals("只改名称", stored.name)
        assertEquals(31, stored.periodValue)
        assertEquals(anchor, stored.anchorDueAt)
        assertEquals(anchor, stored.nextDueAt)
        assertEquals(anchor, stored.lastNotifiedDueAt)
    }
}
