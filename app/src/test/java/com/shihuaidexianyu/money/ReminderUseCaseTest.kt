package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.navigation.MoneyDestination
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ReminderUseCaseTest {
    @Test
    fun `create reminder rejects invalid yearly day for month`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "信用卡",
                initialBalance = 0,
                createdAt = 1L,
            ),
        )
        val useCase = CreateReminderUseCase(
            accountRepository,
            reminderRepository,
            testClockProvider(1_000L),
            { java.time.ZoneId.of("Asia/Shanghai") },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(
                name = "年费",
                type = ReminderType.MANUAL,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 9_999,
                periodType = ReminderPeriodType.YEARLY,
                periodValue = 30,
                periodMonth = 2,
                anchorDueAt = 60_000L,
            )
        }

        assertTrue(error.message?.contains("1 到 28") == true)
    }

    @Test
    fun `update reminder rejects invalid custom interval`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "储蓄卡",
                initialBalance = 0,
                createdAt = 1L,
            ),
        )
        val reminderId = reminderRepository.insertReminder(
            RecurringReminder(
                name = "水费",
                type = ReminderType.MANUAL.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 1_200,
                periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                periodValue = 7,
                periodMonth = null,
                nextDueAt = 100L,
                createdAt = 1L,
                updatedAt = 1L,
                anchorDueAt = 100L,
            ),
        )
        val useCase = UpdateReminderUseCase(
            accountRepository,
            reminderRepository,
            testClockProvider(1_000L),
            { java.time.ZoneId.of("Asia/Shanghai") },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(
                reminderId = reminderId,
                name = "水费",
                type = ReminderType.MANUAL,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 1_200,
                periodType = ReminderPeriodType.CUSTOM_DAYS,
                periodValue = 0,
                periodMonth = null,
                isEnabled = true,
            )
        }

        assertTrue(error.message?.contains("间隔天数") == true)
    }

    @Test
    fun `skip reminder advances exactly one occurrence without confirmation`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "信用卡",
                initialBalance = 0,
                createdAt = 1L,
            ),
        )
        val reminderId = reminderRepository.insertReminder(
            RecurringReminder(
                name = "订阅",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 3_000,
                periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                periodValue = 1,
                periodMonth = null,
                nextDueAt = 60_000L,
                createdAt = 1L,
                updatedAt = 1L,
                anchorDueAt = 60_000L,
            ),
        )
        val useCase = com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase(
            accountRepository,
            reminderRepository,
            clockProvider = { 10L * 86_400_000L },
            zoneIdProvider = { java.time.ZoneId.of("UTC") },
        )

        useCase(reminderId, 60_000L)

        val updated = reminderRepository.getReminderById(reminderId)
        assertTrue(updated != null)
        assertEquals(60_000L + 86_400_000L, updated.nextDueAt)
        assertNull(updated.lastConfirmedAt)
    }

    @Test
    fun `record cash flow route encodes purpose query safely`() {
        val route = MoneyDestination.recordCashFlowRoute(
            direction = CashFlowDirection.OUTFLOW,
            accountId = 7L,
            amount = 1_234L,
            note = "房租 & 水电?#100%",
            reminderId = 9L,
            expectedDueAt = 1_000L,
        )

        assertEquals(
            "records/cashflow/outflow/7?amount=1234&purpose=%E6%88%BF%E7%A7%9F%20%26%20%E6%B0%B4%E7%94%B5%3F%23100%25&reminderId=9&expectedDueAt=1000",
            route,
        )
    }

    @Test
    fun `due reminders refresh when ticker advances`() = runBlocking {
        val ticker = MutableStateFlow(900L)
        val reminderRepository = InMemoryRecurringReminderRepository(ticker)
        val reminderId = reminderRepository.insertReminder(
            RecurringReminder(
                name = "宽带",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200L,
                periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                periodValue = 30,
                periodMonth = null,
                nextDueAt = 1_000L,
                createdAt = 1L,
                updatedAt = 1L,
                anchorDueAt = 1_000L,
            ),
        )

        assertTrue(reminderRepository.observeDueReminders().first().isEmpty())

        ticker.value = 1_100L

        assertEquals(
            listOf(reminderId),
            reminderRepository.observeDueReminders().first().map { it.id },
        )
    }
}
