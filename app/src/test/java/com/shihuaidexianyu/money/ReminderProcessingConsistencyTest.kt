package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ReminderProcessingConsistencyTest {
    @Test
    fun `skipping closed account reminder fails without advancing reminder`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "旧账户", initialBalance = 0L, createdAt = 1L),
        )
        accountRepository.closeAccount(accountId, closedAt = 2L)
        val reminderId = reminderRepository.insertReminder(
            testReminder(accountId = accountId, nextDueAt = 1_000L),
        )
        val before = requireNotNull(reminderRepository.getReminderById(reminderId))
        val useCase = SkipReminderUseCase(
            accountRepository,
            reminderRepository,
            clockProvider = { 2_000L },
            zoneIdProvider = { java.time.ZoneId.of("UTC") },
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(reminderId, 1_000L)
        }

        assertEquals(before, reminderRepository.getReminderById(reminderId))
    }

    @Test
    fun `skipping active reminder advances one due and leaves confirmation untouched`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "信用卡", initialBalance = 0L, createdAt = 1L),
        )
        val reminderId = reminderRepository.insertReminder(
            testReminder(
                accountId = accountId,
                nextDueAt = 60_000L,
            ),
        )
        val useCase = SkipReminderUseCase(
            accountRepository,
            reminderRepository,
            clockProvider = { 3L * 86_400_000L },
            zoneIdProvider = { java.time.ZoneId.of("UTC") },
        )

        useCase(reminderId, 60_000L)

        val updated = assertNotNull(reminderRepository.getReminderById(reminderId))
        assertEquals(60_000L + 86_400_000L, updated.nextDueAt)
        assertNull(updated.lastConfirmedAt)
    }

    private fun testReminder(
        accountId: Long,
        nextDueAt: Long,
    ): RecurringReminder {
        return RecurringReminder(
            name = "订阅",
            type = ReminderType.SUBSCRIPTION.value,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 1_000L,
            periodType = ReminderPeriodType.CUSTOM_DAYS.value,
            periodValue = 1,
            periodMonth = null,
            nextDueAt = nextDueAt,
            createdAt = 1L,
            updatedAt = 1L,
            anchorDueAt = nextDueAt,
        )
    }
}
