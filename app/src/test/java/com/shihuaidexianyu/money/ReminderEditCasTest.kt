package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReminderEditCasTest {
    @Test
    fun `edit winning after process read makes process advance fail and ledger roll back`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()
        var edited = false
        val interleavingRepository = object : RecurringReminderRepository by fixture.reminders {
            override suspend fun advanceOccurrence(
                reminderId: Long,
                expectedDueAt: Long,
                expectedUpdatedAt: Long,
                nextDueAt: Long,
                confirmedAt: Long,
                updatedAt: Long,
            ): Boolean {
                if (!edited) {
                    edited = true
                    fixture.edit(before, "edited while processing")
                }
                return fixture.reminders.advanceOccurrence(
                    reminderId,
                    expectedDueAt,
                    expectedUpdatedAt,
                    nextDueAt,
                    confirmedAt,
                    updatedAt,
                )
            }
        }
        val process = fixture.processUseCase(interleavingRepository)

        assertFailsWith<IllegalStateException> {
            fixture.process(process, before.nextDueAt)
        }

        assertTrue(fixture.transactions.queryAllCashFlowRecords().isEmpty())
        assertEquals("edited while processing", fixture.reminder().name)
        assertEquals(before.nextDueAt, fixture.reminder().nextDueAt)
    }

    @Test
    fun `process winning makes stale edit fail without reverting occurrence`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()

        fixture.process(fixture.processUseCase(fixture.reminders), before.nextDueAt)
        val advanced = fixture.reminder()

        assertFailsWith<IllegalStateException> { fixture.edit(before, "stale edit") }
        assertEquals(advanced.nextDueAt, fixture.reminder().nextDueAt)
        assertEquals(advanced.lastConfirmedAt, fixture.reminder().lastConfirmedAt)
    }

    @Test
    fun `skip winning makes stale edit fail without reverting occurrence`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()
        SkipReminderUseCase(
            fixture.accounts,
            fixture.reminders,
            clockProvider = { fixture.now },
            zoneIdProvider = { fixture.zone },
        )(fixture.reminderId, before.nextDueAt)
        val advanced = fixture.reminder()

        assertFailsWith<IllegalStateException> { fixture.edit(before, "stale edit") }
        assertEquals(advanced.nextDueAt, fixture.reminder().nextDueAt)
        assertEquals(before.lastConfirmedAt, fixture.reminder().lastConfirmedAt)
    }

    @Test
    fun `edit winning rejects stale process and skip version boundaries`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()
        fixture.edit(before, "winner")

        assertFalse(
            fixture.reminders.advanceOccurrence(
                reminderId = fixture.reminderId,
                expectedDueAt = before.nextDueAt,
                expectedUpdatedAt = before.updatedAt,
                nextDueAt = before.nextDueAt + DAY,
                confirmedAt = fixture.now,
                updatedAt = fixture.now,
            ),
        )
        assertFalse(
            fixture.reminders.skipOccurrence(
                reminderId = fixture.reminderId,
                expectedDueAt = before.nextDueAt,
                expectedUpdatedAt = before.updatedAt,
                advancedDueAt = before.nextDueAt + DAY,
                skippedUpdatedAt = fixture.now,
            ),
        )
    }

    @Test
    fun `notification acknowledgement racing metadata edit is preserved`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()
        assertTrue(
            fixture.reminders.acknowledgeNotifiedOccurrence(fixture.reminderId, before.nextDueAt),
        )

        assertTrue(
            fixture.reminders.updateReminderIfUnchanged(
                reminder = before.copy(name = "metadata", updatedAt = before.updatedAt + 1),
                expectedUpdatedAt = before.updatedAt,
                clearNotificationCursor = false,
            ),
        )

        assertEquals(before.nextDueAt, fixture.reminder().lastNotifiedDueAt)
    }

    private class Fixture {
        val now = 120_000L
        val zone = ZoneId.of("UTC")
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val transactions = InMemoryTransactionRepository()
        val accountId = runBlocking {
            accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        }
        val reminderId = runBlocking {
            reminders.insertReminder(
                RecurringReminder(
                    name = "bill",
                    type = ReminderType.MANUAL.value,
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100,
                    periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                    periodValue = 1,
                    periodMonth = null,
                    nextDueAt = 60_000L,
                    anchorDueAt = 60_000L,
                    createdAt = 1,
                    updatedAt = 100,
                ),
            )
        }

        suspend fun reminder() = requireNotNull(reminders.getReminderById(reminderId))

        suspend fun edit(snapshot: RecurringReminder, name: String) = UpdateReminderUseCase(
            accounts,
            reminders,
            clockProvider = { now },
            zoneIdProvider = { zone },
        )(
            reminderId = reminderId,
            name = name,
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS,
            periodValue = 1,
            periodMonth = null,
            anchorDueAt = null,
            isEnabled = true,
            expectedUpdatedAt = snapshot.updatedAt,
        )

        fun processUseCase(repository: RecurringReminderRepository) = ProcessDueReminderUseCase(
            accounts,
            transactions,
            repository,
            RefreshAccountActivityStateUseCase(accounts, transactions),
            clockProvider = { now },
            zoneIdProvider = { zone },
        )

        suspend fun process(useCase: ProcessDueReminderUseCase, dueAt: Long) = useCase(
            reminderId = reminderId,
            expectedDueAt = dueAt,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            occurredAt = dueAt,
            amount = 100,
            note = "bill",
        )
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
