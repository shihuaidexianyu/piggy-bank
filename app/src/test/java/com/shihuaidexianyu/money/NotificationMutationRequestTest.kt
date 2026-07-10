package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.NotificationSyncingAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test

class NotificationMutationRequestTest {
    @Test
    fun `reminder create update skip and delete request debounced sync`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val requester = RecordingRequester()
        val accountId = accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        val clock = testClockProvider(1_000L)
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider {
            java.time.ZoneId.of("Asia/Shanghai")
        }
        val reminderId = CreateReminderUseCase(accounts, reminders, clock, zone, requester)(
            name = "bill",
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS,
            periodValue = 1,
            periodMonth = null,
            anchorDueAt = 2_000L,
        )
        UpdateReminderUseCase(accounts, reminders, clock, zone, requester)(
            reminderId = reminderId,
            name = "new bill",
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS,
            periodValue = 1,
            periodMonth = null,
            isEnabled = true,
        )
        SkipReminderUseCase(accounts, reminders, { 3_000L }, zone, requester)(reminderId, 2_000L)
        DeleteReminderUseCase(accounts, reminders, requester)(reminderId)

        assertEquals(
            listOf(
                NotificationSyncReason.REMINDER_CHANGED,
                NotificationSyncReason.REMINDER_CHANGED,
                NotificationSyncReason.REMINDER_SKIPPED,
                NotificationSyncReason.REMINDER_CHANGED,
            ),
            requester.reasons,
        )
    }

    @Test
    fun `config mutations request sync but cursor acknowledgement does not`() = runBlocking {
        val requester = RecordingRequester()
        val repository = NotificationSyncingAccountReminderSettingsRepository(
            InMemoryAccountReminderSettingsRepository(),
            requester,
        )
        repository.updateReminderConfig(1, BalanceUpdateReminderConfig())
        repository.compareAndSetLastNotifiedBoundary(1, null, 100)
        repository.resetLastNotifiedBoundary(1)
        repository.setEnabled(1, false)

        assertEquals(
            listOf(NotificationSyncReason.CONFIG_CHANGED, NotificationSyncReason.CONFIG_CHANGED),
            requester.reasons,
        )
    }

    @Test
    fun `process balance reconcile and close request sync after successful mutation`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val transactions = InMemoryTransactionRepository()
        val configs = InMemoryAccountReminderSettingsRepository()
        val requester = RecordingRequester()
        val clock = testClockProvider(10_000)
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider {
            java.time.ZoneId.of("UTC")
        }
        val accountId = accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        configs.updateReminderConfig(accountId, BalanceUpdateReminderConfig())
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        val reminderId = reminders.insertReminder(testReminder(accountId, 1_000))

        ProcessDueReminderUseCase(
            accounts,
            transactions,
            reminders,
            refresh,
            clock,
            zone,
            requester,
        )(
            reminderId = reminderId,
            expectedDueAt = 1_000,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            occurredAt = 2_000,
            amount = 100,
            note = "bill",
        )
        val closeBalance = UpdateBalanceUseCase(
            accounts,
            transactions,
            ResolveBalanceUpdateContextUseCase(accounts, transactions),
            refresh,
            clock,
            requester,
        )(accountId, actualBalance = -100, occurredAt = 3_000, operationId = "balance-sync")
        // Settle back to zero so the close operation is valid.
        UpdateBalanceUseCase(
            accounts,
            transactions,
            ResolveBalanceUpdateContextUseCase(accounts, transactions),
            refresh,
            clock,
            requester,
        )(accountId, actualBalance = 0, occurredAt = 4_000, operationId = "balance-close")
        UpdateBalanceUpdateRecordUseCase(
            accounts,
            transactions,
            ResolveBalanceUpdateContextUseCase(accounts, transactions),
            refresh,
            clock,
            requester,
        )(closeBalance.insertResult.recordId, actualBalance = 0, occurredAt = 4_000)
        val undo = requireNotNull(
            DeleteBalanceUpdateRecordUseCase(accounts, transactions, refresh, clock, requester)(
                closeBalance.insertResult.recordId,
            ),
        )
        RestoreLedgerRecordUseCase(accounts, transactions, refresh, clock, requester)(undo)
        CloseAccountUseCase(
            accounts,
            reminders,
            CalculateCurrentBalanceUseCase(accounts, transactions, clock),
            transactions,
            clock,
            AccountLifecycleCoordinator(),
            configs,
            requester,
        )(accountId)

        assertEquals(
            listOf(
                NotificationSyncReason.REMINDER_PROCESSED,
                NotificationSyncReason.BALANCE_RECONCILED,
                NotificationSyncReason.BALANCE_RECONCILED,
                NotificationSyncReason.BALANCE_RECONCILED,
                NotificationSyncReason.BALANCE_RECONCILED,
                NotificationSyncReason.BALANCE_RECONCILED,
                NotificationSyncReason.ACCOUNT_CLOSED,
            ),
            requester.reasons,
        )
    }

    @Test
    fun `enqueue failure never rolls back reminder mutation`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val accountId = accounts.createAccount(Account(name = "wallet", initialBalance = 0, createdAt = 1))
        val throwingRequester = NotificationSyncRequester { error("work manager unavailable") }

        val id = CreateReminderUseCase(
            accounts,
            reminders,
            testClockProvider(1_000L),
            { java.time.ZoneId.of("Asia/Shanghai") },
            throwingRequester,
        )(
            name = "bill",
            type = ReminderType.MANUAL,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS,
            periodValue = 1,
            periodMonth = null,
            anchorDueAt = 60_000L,
        )

        assertEquals("bill", reminders.getReminderById(id)?.name)
    }

    private fun testReminder(accountId: Long, dueAt: Long) =
        com.shihuaidexianyu.money.domain.model.RecurringReminder(
            name = "bill",
            type = ReminderType.MANUAL.value,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS.value,
            periodValue = 1,
            periodMonth = null,
            nextDueAt = dueAt,
            anchorDueAt = dueAt,
            createdAt = 1,
            updatedAt = 1,
        )

    private class RecordingRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()
        override fun request(reason: NotificationSyncReason) {
            reasons += reason
        }
    }
}
