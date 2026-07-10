package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ReminderProcessingConsistencyTest {
    @Test
    fun `confirming closed account reminder fails without advancing reminder`() = runBlocking {
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
        val useCase = ConfirmReminderUseCase(accountRepository, reminderRepository)

        assertFailsWith<IllegalArgumentException> {
            useCase(reminderId)
        }

        assertEquals(before, reminderRepository.getReminderById(reminderId))
    }

    @Test
    fun `confirming active reminder advances next due and records confirmation time together`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "信用卡", initialBalance = 0L, createdAt = 1L),
        )
        val reminderId = reminderRepository.insertReminder(
            testReminder(
                accountId = accountId,
                nextDueAt = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L,
            ),
        )
        val useCase = ConfirmReminderUseCase(accountRepository, reminderRepository)

        useCase(reminderId)

        val updated = assertNotNull(reminderRepository.getReminderById(reminderId))
        assertTrue(updated.nextDueAt > System.currentTimeMillis())
        assertNotNull(updated.lastConfirmedAt)
        assertEquals(updated.lastConfirmedAt, updated.updatedAt)
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
