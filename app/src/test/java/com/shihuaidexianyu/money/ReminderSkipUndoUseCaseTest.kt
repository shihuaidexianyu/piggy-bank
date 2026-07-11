package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UndoSkipReminderUseCase
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ReminderSkipUndoUseCaseTest {
    @Test
    fun `skip advances exactly one occurrence without ledger or confirmation`() = runBlocking {
        val fixture = Fixture()
        val before = fixture.reminder()

        val token = fixture.skip(before.nextDueAt)

        val after = fixture.reminder()
        assertEquals(before.nextDueAt + DAY, after.nextDueAt)
        assertEquals(before.lastConfirmedAt, after.lastConfirmedAt)
        assertTrue(fixture.transactions.queryAllCashFlowRecords().isEmpty())
        assertEquals(before.nextDueAt, token.skippedDueAt)
        assertEquals(after.nextDueAt, token.advancedDueAt)
        assertEquals(listOf(NotificationSyncReason.REMINDER_SKIPPED), fixture.requester.reasons)
    }

    @Test
    fun `undo restores exact skipped occurrence and requests sync`() = runBlocking {
        val fixture = Fixture()
        val dueAt = fixture.reminder().nextDueAt
        val token = fixture.skip(dueAt)

        val result = fixture.undo(token)

        assertEquals(UndoReminderSkipResult.RESTORED, result)
        assertEquals(dueAt, fixture.reminder().nextDueAt)
        assertEquals(
            listOf(NotificationSyncReason.REMINDER_SKIPPED, NotificationSyncReason.REMINDER_UNDO),
            fixture.requester.reasons,
        )
    }

    @Test
    fun `replayed undo after crash is already restored`() = runBlocking {
        val fixture = Fixture()
        val token = fixture.skip(fixture.reminder().nextDueAt)
        assertEquals(UndoReminderSkipResult.RESTORED, fixture.undo(token))
        assertEquals(UndoReminderSkipResult.ALREADY_RESTORED, fixture.undo(token))
        assertEquals(
            listOf(
                NotificationSyncReason.REMINDER_SKIPPED,
                NotificationSyncReason.REMINDER_UNDO,
                NotificationSyncReason.REMINDER_UNDO,
            ),
            fixture.requester.reasons,
        )
    }

    @Test
    fun `process and skip racing same occurrence have one winner`() = runBlocking {
        val fixture = Fixture()
        val dueAt = fixture.reminder().nextDueAt

        val results = withContext(Dispatchers.Default) {
            listOf(
                async { runCatching { fixture.skip(dueAt); "skip" } },
                async { runCatching { fixture.process(dueAt); "process" } },
            ).awaitAll()
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(dueAt + DAY, fixture.reminder().nextDueAt)
        assertTrue(fixture.transactions.queryAllCashFlowRecords().size in 0..1)
        if (results.single { it.isSuccess }.getOrThrow() == "process") {
            assertEquals("cash:reminder:${fixture.reminderId}:$dueAt", fixture.transactions.queryAllCashFlowRecords().single().operationId)
        } else {
            assertTrue(fixture.transactions.queryAllCashFlowRecords().isEmpty())
        }
    }

    @Test
    fun `undo is stale after edit second skip process or pause`() = runBlocking {
        suspend fun assertStale(mutate: suspend (Fixture, ReminderSkipUndoToken) -> Unit) {
            val fixture = Fixture()
            val token = fixture.skip(fixture.reminder().nextDueAt)
            mutate(fixture, token)
            assertEquals(UndoReminderSkipResult.STALE, fixture.undo(token))
        }

        assertStale { fixture, _ ->
            val row = fixture.reminder()
            fixture.reminders.updateReminder(row.copy(name = "edited", updatedAt = row.updatedAt + 1))
        }
        assertStale { fixture, token -> fixture.skip(token.advancedDueAt) }
        assertStale { fixture, token -> fixture.process(token.advancedDueAt) }
        assertStale { fixture, _ ->
            val row = fixture.reminder()
            fixture.reminders.updateReminder(row.copy(isEnabled = false, updatedAt = row.updatedAt + 1))
        }
    }

    @Test
    fun `undo reports not found after delete and wrong token is safe`() = runBlocking {
        val fixture = Fixture()
        val token = fixture.skip(fixture.reminder().nextDueAt)
        val wrong = token.copy(skippedUpdatedAt = token.skippedUpdatedAt + 1)

        assertEquals(UndoReminderSkipResult.STALE, fixture.undo(wrong))
        fixture.reminders.deleteReminder(fixture.reminderId)
        assertEquals(UndoReminderSkipResult.NOT_FOUND, fixture.undo(token))
        assertNull(fixture.reminders.getReminderById(fixture.reminderId))
    }

    private class Fixture {
        val now = 20L * DAY
        val zone = ZoneId.of("UTC")
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val transactions = InMemoryTransactionRepository()
        val requester = RecordingRequester()
        val accountId = runBlocking {
            accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        }
        val reminderId = runBlocking {
            reminders.insertReminder(
                RecurringReminder(
                    name = "bill",
                    type = "manual",
                    accountId = accountId,
                    direction = "outflow",
                    amount = 100,
                    periodType = "custom_days",
                    periodValue = 1,
                    periodMonth = null,
                    nextDueAt = 10L * DAY,
                    anchorDueAt = 10L * DAY,
                    lastConfirmedAt = 5L * DAY,
                    createdAt = 1,
                    updatedAt = 2,
                ),
            )
        }
        private val skipUseCase = SkipReminderUseCase(
            accounts,
            reminders,
            clockProvider = { now },
            zoneIdProvider = { zone },
            notificationSyncRequester = requester,
        )
        private val undoUseCase = UndoSkipReminderUseCase(
            reminders,
            clockProvider = { now },
            notificationSyncRequester = requester,
        )
        private val processUseCase = ProcessDueReminderUseCase(
            accounts,
            transactions,
            reminders,
            RefreshAccountActivityStateUseCase(accounts, transactions),
            clockProvider = { now },
            zoneIdProvider = { zone },
            notificationSyncRequester = requester,
        )

        suspend fun reminder() = requireNotNull(reminders.getReminderById(reminderId))
        suspend fun skip(expectedDueAt: Long) = skipUseCase(reminderId, expectedDueAt)
        suspend fun undo(token: ReminderSkipUndoToken) = undoUseCase(token)
        suspend fun process(expectedDueAt: Long) = processUseCase(
            reminderId = reminderId,
            expectedDueAt = expectedDueAt,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            occurredAt = expectedDueAt,
            amount = 100,
            note = "bill",
        )
    }

    private class RecordingRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()
        override fun request(reason: NotificationSyncReason) {
            reasons += reason
        }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
