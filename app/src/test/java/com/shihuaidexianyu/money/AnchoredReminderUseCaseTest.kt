package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test

class AnchoredReminderUseCaseTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = at(2026, 1, 10, 10, 0)

    @Test
    fun `creation stores selected future anchor and rejects past anchor`() = runBlocking {
        val fixture = Fixture()
        val anchor = at(2026, 1, 31, 18, 45)

        val id = fixture.create(anchor)

        val stored = requireNotNull(fixture.reminders.getReminderById(id))
        assertEquals(anchor, stored.anchorDueAt)
        assertEquals(anchor, stored.nextDueAt)
        assertFailsWith<IllegalArgumentException> { fixture.create(now - 1) }
        Unit
    }

    @Test
    fun `metadata edit preserves anchor next due and notification cursor`() = runBlocking {
        val fixture = Fixture()
        val anchor = at(2026, 1, 31, 18, 45)
        val id = fixture.reminders.insertReminder(
            fixture.reminder(anchor).copy(lastNotifiedDueAt = anchor),
        )

        fixture.update(
            reminderId = id,
            name = "renamed",
            anchorDueAt = anchor,
            periodType = ReminderPeriodType.MONTHLY,
            periodValue = 31,
        )

        val stored = requireNotNull(fixture.reminders.getReminderById(id))
        assertEquals(anchor, stored.anchorDueAt)
        assertEquals(anchor, stored.nextDueAt)
        assertEquals(anchor, stored.lastNotifiedDueAt)
        assertEquals("renamed", stored.name)
    }

    @Test
    fun `metadata edit after timezone change preserves stored schedule without reinterpretation`() = runBlocking {
        val fixture = Fixture()
        val anchor = ZonedDateTime.of(2026, 1, 31, 0, 30, 45, 123_000_000, zone)
            .toInstant().toEpochMilli()
        val id = fixture.reminders.insertReminder(
            fixture.reminder(anchor).copy(lastNotifiedDueAt = anchor),
        )
        val updateInNewYork = UpdateReminderUseCase(
            fixture.accounts,
            fixture.reminders,
            clockProvider = { now },
            zoneIdProvider = { ZoneId.of("America/New_York") },
        )

        updateInNewYork(
            reminderId = id,
            name = "renamed after zone change",
            type = ReminderType.MANUAL,
            accountId = fixture.accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.MONTHLY,
            periodValue = 31,
            periodMonth = null,
            anchorDueAt = null,
            isEnabled = true,
        )

        val stored = requireNotNull(fixture.reminders.getReminderById(id))
        assertEquals(anchor, stored.anchorDueAt)
        assertEquals(anchor, stored.nextDueAt)
        assertEquals(anchor, stored.lastNotifiedDueAt)
    }

    @Test
    fun `schedule edit advances historical anchor to first occurrence at or after now and resets cursor`() =
        runBlocking {
            val fixture = Fixture()
            val oldAnchor = at(2025, 12, 1, 8, 0)
            val newAnchor = at(2026, 1, 1, 9, 15)
            val id = fixture.reminders.insertReminder(
                fixture.reminder(oldAnchor).copy(lastNotifiedDueAt = oldAnchor),
            )

            fixture.update(
                reminderId = id,
                name = "bill",
                anchorDueAt = newAnchor,
                periodType = ReminderPeriodType.CUSTOM_DAYS,
                periodValue = 3,
            )

            val stored = requireNotNull(fixture.reminders.getReminderById(id))
            assertEquals(newAnchor, stored.anchorDueAt)
            assertEquals(at(2026, 1, 13, 9, 15), stored.nextDueAt)
            assertNull(stored.lastNotifiedDueAt)
        }

    private inner class Fixture {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val accountId = runBlocking {
            accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        }
        private val createUseCase = CreateReminderUseCase(
            accountRepository = accounts,
            reminderRepository = reminders,
            clockProvider = { now },
            zoneIdProvider = { zone },
        )
        private val updateUseCase = UpdateReminderUseCase(
            accountRepository = accounts,
            reminderRepository = reminders,
            clockProvider = { now },
            zoneIdProvider = { zone },
        )

        suspend fun create(anchorDueAt: Long): Long = createUseCase(
            name = "bill",
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.MONTHLY,
            periodValue = 31,
            periodMonth = null,
            anchorDueAt = anchorDueAt,
        )

        suspend fun update(
            reminderId: Long,
            name: String,
            anchorDueAt: Long,
            periodType: ReminderPeriodType,
            periodValue: Int,
        ) = updateUseCase(
            reminderId = reminderId,
            name = name,
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = periodType,
            periodValue = periodValue,
            periodMonth = null,
            anchorDueAt = anchorDueAt,
            isEnabled = true,
        )

        fun reminder(anchor: Long) = RecurringReminder(
            name = "bill",
            type = ReminderType.MANUAL.value,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 100,
            periodType = ReminderPeriodType.MONTHLY.value,
            periodValue = 31,
            periodMonth = null,
            nextDueAt = anchor,
            anchorDueAt = anchor,
            createdAt = anchor,
            updatedAt = anchor,
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
