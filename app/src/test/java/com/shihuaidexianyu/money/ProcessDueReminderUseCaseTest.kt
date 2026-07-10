package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProcessDueReminderUseCaseTest {
    @Test
    fun `equal occurrence replay returns original cash and does not advance twice`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, dueAt) = fixture.createReminder()

        val firstId = fixture.process(reminderId, dueAt)
        val afterFirst = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))
        val replayId = fixture.process(reminderId, dueAt)
        val afterReplay = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))

        val record = fixture.transactionRepository.queryAllCashFlowRecords().single()
        assertEquals(firstId, replayId)
        assertEquals("cash:reminder:$reminderId:$dueAt", record.operationId)
        assertEquals(afterFirst.nextDueAt, afterReplay.nextDueAt)
        assertEquals(afterFirst.lastConfirmedAt, afterReplay.lastConfirmedAt)
    }

    @Test
    fun `same occurrence with different payload conflicts`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, dueAt) = fixture.createReminder()
        fixture.process(reminderId, dueAt)

        assertFailsWith<LedgerOperationConflictException> {
            fixture.process(reminderId, dueAt, amount = 1_201)
        }
        assertEquals(1, fixture.transactionRepository.queryAllCashFlowRecords().size)
    }

    @Test
    fun `stale expected due fails without cash insertion`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, dueAt) = fixture.createReminder()

        assertFailsWith<IllegalStateException> {
            fixture.process(reminderId, dueAt - 1)
        }
        assertTrue(fixture.transactionRepository.queryAllCashFlowRecords().isEmpty())
    }

    @Test
    fun `next occurrence uses a different deterministic operation id`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, firstDueAt) = fixture.createReminder()
        fixture.process(reminderId, firstDueAt)
        val secondDueAt = requireNotNull(fixture.reminderRepository.getReminderById(reminderId)).nextDueAt
        fixture.clock.now = secondDueAt

        fixture.process(reminderId, secondDueAt, occurredAt = secondDueAt)

        assertEquals(
            setOf(
                "cash:reminder:$reminderId:$firstDueAt",
                "cash:reminder:$reminderId:$secondDueAt",
            ),
            fixture.transactionRepository.queryAllCashFlowRecords().map { it.operationId }.toSet(),
        )
    }

    @Test
    fun `concurrent processing inserts once and progresses once`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, dueAt) = fixture.createReminder()
        val before = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))

        val ids = withContext(Dispatchers.Default) {
            List(16) { async { fixture.process(reminderId, dueAt) } }.awaitAll()
        }

        val after = requireNotNull(fixture.reminderRepository.getReminderById(reminderId))
        assertEquals(1, ids.distinct().size)
        assertEquals(1, fixture.transactionRepository.queryAllCashFlowRecords().size)
        assertTrue(after.nextDueAt > before.nextDueAt)
        assertEquals(fixture.clock.now, after.lastConfirmedAt)
    }

    @Test
    fun `archived account blocks a genuinely new reminder occurrence`() = runBlocking {
        val fixture = ReminderProcessFixture()
        val (reminderId, dueAt) = fixture.createReminder()
        fixture.accountRepository.archiveAccount(fixture.accountId, archivedAt = 2)

        assertFailsWith<IllegalArgumentException> {
            fixture.process(reminderId, dueAt)
        }
        assertTrue(fixture.transactionRepository.queryAllCashFlowRecords().isEmpty())
    }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    private class ReminderProcessFixture {
        val clock = MutableClock(now = 100_000)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountId = runBlocking {
            accountRepository.createAccount(Account(name = "信用卡", initialBalance = 0, createdAt = 1))
        }
        val useCase = ProcessDueReminderUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            reminderRepository = reminderRepository,
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
                accountRepository = accountRepository,
                transactionRepository = transactionRepository,
            ),
            clockProvider = clock,
        )

        suspend fun createReminder(dueAt: Long = 1_000): Pair<Long, Long> {
            val reminderId = reminderRepository.insertReminder(testReminder(accountId, dueAt))
            return reminderId to dueAt
        }

        suspend fun process(
            reminderId: Long,
            expectedDueAt: Long,
            occurredAt: Long = 900,
            amount: Long = 1_200,
        ): Long = useCase(
            reminderId = reminderId,
            expectedDueAt = expectedDueAt,
            occurredAt = occurredAt,
            amount = amount,
            note = "会员续费",
        )
    }

    private companion object {
        fun testReminder(accountId: Long, nextDueAt: Long) = RecurringReminder(
            name = "订阅",
            type = ReminderType.SUBSCRIPTION.value,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 1_000,
            periodType = ReminderPeriodType.CUSTOM_DAYS.value,
            periodValue = 1,
            periodMonth = null,
            nextDueAt = nextDueAt,
            createdAt = 1,
            updatedAt = 1,
            anchorDueAt = nextDueAt,
        )
    }
}
