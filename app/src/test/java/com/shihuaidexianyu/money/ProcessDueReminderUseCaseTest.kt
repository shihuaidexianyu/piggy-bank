package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ProcessDueReminderUseCaseTest {
    @Test
    fun `due reminder creates cash flow and advances next due date together`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val accountId = fixture.accountRepository.createAccount(
            Account(name = "信用卡", initialBalance = 0L, createdAt = 1L),
        )
        val reminderId = fixture.reminderRepository.insertReminder(
            testReminder(
                accountId = accountId,
                nextDueAt = System.currentTimeMillis() - 3L * DAY_MILLIS,
            ),
        )

        val recordId = fixture.useCase(
            reminderId = reminderId,
            occurredAt = System.currentTimeMillis() - 1_000L,
            amount = 1_200L,
            purpose = "会员续费",
        )

        val records = fixture.transactionRepository.queryAllActiveCashFlowRecords()
        val updatedReminder = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))
        assertEquals(recordId, records.single().id)
        assertEquals(accountId, records.single().accountId)
        assertEquals(CashFlowDirection.OUTFLOW.value, records.single().direction)
        assertEquals(1_200L, records.single().amount)
        assertEquals("会员续费", records.single().purpose)
        assertTrue(updatedReminder.nextDueAt > System.currentTimeMillis())
        assertTrue(updatedReminder.lastConfirmedAt != null)
    }

    @Test
    fun `cash flow validation failure does not advance reminder`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val accountId = fixture.accountRepository.createAccount(
            Account(name = "信用卡", initialBalance = 0L, createdAt = 1L),
        )
        val reminderId = fixture.reminderRepository.insertReminder(testReminder(accountId = accountId, nextDueAt = 1_000L))
        val before = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))

        assertFailsWith<IllegalArgumentException> {
            fixture.useCase(
                reminderId = reminderId,
                occurredAt = System.currentTimeMillis() + DAY_MILLIS,
                amount = 1_200L,
                purpose = "未来记录",
            )
        }

        assertTrue(fixture.transactionRepository.queryAllActiveCashFlowRecords().isEmpty())
        assertEquals(before, fixture.reminderRepository.getReminderById(reminderId))
    }

    @Test
    fun `archived account reminder cannot be processed`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val accountId = fixture.accountRepository.createAccount(
            Account(name = "旧账户", initialBalance = 0L, createdAt = 1L),
        )
        fixture.accountRepository.archiveAccount(accountId, archivedAt = 2L)
        val reminderId = fixture.reminderRepository.insertReminder(testReminder(accountId = accountId, nextDueAt = 1_000L))
        val before = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))

        assertFailsWith<IllegalArgumentException> {
            fixture.useCase(
                reminderId = reminderId,
                occurredAt = System.currentTimeMillis() - 1_000L,
                amount = 1_200L,
                purpose = "归档账户",
            )
        }

        assertTrue(fixture.transactionRepository.queryAllActiveCashFlowRecords().isEmpty())
        assertEquals(before, fixture.reminderRepository.getReminderById(reminderId))
    }

    private class ReminderProcessFixture {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val useCase = ProcessDueReminderUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            reminderRepository = reminderRepository,
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
            ),
        )
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
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
