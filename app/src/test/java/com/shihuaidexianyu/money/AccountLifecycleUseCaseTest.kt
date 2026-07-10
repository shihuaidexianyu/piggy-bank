package com.shihuaidexianyu.money

import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountClosureIssuesUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.SetAccountHiddenUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountLifecycleUseCaseTest {
    @Test
    fun closeRollsBackWhenReminderDisableFailsBeforeAccountMutation() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val reminderDelegate = InMemoryRecurringReminderRepository(MutableStateFlow(10_000))
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val reminderId = reminderDelegate.insertReminder(reminder(accountId, enabled = true, updatedAt = 1))
        val failingReminders = object : RecurringReminderRepository by reminderDelegate {
            override suspend fun disableEnabledForAccount(accountId: Long, updatedAt: Long) {
                error("injected reminder failure")
            }
        }
        val close = CloseAccountUseCase(
            accounts,
            failingReminders,
            CalculateCurrentBalanceUseCase(accounts, transactions, testClockProvider(10_000)),
            transactions,
            testClockProvider(10_000),
        )

        assertFailsWith<IllegalStateException> { close(accountId) }
        assertNull(accounts.getAccountById(accountId)?.closedAt)
        assertTrue(requireNotNull(reminderDelegate.getReminderById(reminderId)).isEnabled)
    }

    @Test
    fun closeZeroBalance_capturesOneTimeAndDisablesOnlyEnabledRemindersForThatAccount() = runBlocking {
        val fixture = Fixture(now = 8_000)
        val closingId = fixture.createAccount("待关闭", initialBalance = 0)
        val otherId = fixture.createAccount("其他", initialBalance = 0)
        val enabledId = fixture.reminders.insertReminder(reminder(closingId, enabled = true, updatedAt = 10))
        val disabledId = fixture.reminders.insertReminder(reminder(closingId, enabled = false, updatedAt = 11))
        val otherReminderId = fixture.reminders.insertReminder(reminder(otherId, enabled = true, updatedAt = 12))

        fixture.closeAccount(closingId)

        assertEquals(1, fixture.clock.calls)
        assertEquals(8_000, fixture.accounts.getAccountById(closingId)?.closedAt)
        assertFalse(requireNotNull(fixture.reminders.getReminderById(enabledId)).isEnabled)
        assertEquals(8_000, fixture.reminders.getReminderById(enabledId)?.updatedAt)
        assertFalse(requireNotNull(fixture.reminders.getReminderById(disabledId)).isEnabled)
        assertEquals(11, fixture.reminders.getReminderById(disabledId)?.updatedAt)
        assertTrue(requireNotNull(fixture.reminders.getReminderById(otherReminderId)).isEnabled)
        assertEquals(12, fixture.reminders.getReminderById(otherReminderId)?.updatedAt)
    }

    @Test
    fun closePositiveAndNegativeBalances_rollBackWithoutChangingAccountOrReminders() = runBlocking {
        for (direction in listOf(CashFlowDirection.INFLOW, CashFlowDirection.OUTFLOW)) {
            val fixture = Fixture(now = 8_000)
            val accountId = fixture.createAccount(direction.value, initialBalance = 0)
            fixture.transactions.insertCashFlowRecord(
                cashFlow(accountId, direction, amount = 10, operationId = "close-${direction.value}"),
            )
            val reminderId = fixture.reminders.insertReminder(reminder(accountId, enabled = true, updatedAt = 10))

            val error = assertFailsWith<IllegalArgumentException> { fixture.closeAccount(accountId) }

            assertTrue(error.message.orEmpty().contains("余额必须为 0"))
            assertNull(fixture.accounts.getAccountById(accountId)?.closedAt)
            assertTrue(requireNotNull(fixture.reminders.getReminderById(reminderId)).isEnabled)
            assertEquals(10, fixture.reminders.getReminderById(reminderId)?.updatedAt)
        }
    }

    @Test
    fun closeRejectsMissingAndAlreadyClosedAccounts() = runBlocking {
        val fixture = Fixture(now = 8_000)
        val accountId = fixture.createAccount("已关闭", initialBalance = 0)
        fixture.accounts.closeAccount(accountId, 7_000)

        assertEquals("账户不存在", assertFailsWith<IllegalArgumentException> { fixture.closeAccount(999) }.message)
        assertEquals("账户已关闭", assertFailsWith<IllegalArgumentException> { fixture.closeAccount(accountId) }.message)
        assertEquals(7_000, fixture.accounts.getAccountById(accountId)?.closedAt)
    }

    @Test
    fun hideIsIdempotentAndRequiresAnExistingOpenAccount() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.createAccount("账户", initialBalance = 20)
        val useCase = SetAccountHiddenUseCase(fixture.accounts, fixture.transactions)

        useCase(accountId, true)
        useCase(accountId, true)
        assertTrue(requireNotNull(fixture.accounts.getAccountById(accountId)).isHidden)
        assertEquals(20, fixture.accounts.getAccountById(accountId)?.initialBalance)

        fixture.accounts.closeAccount(accountId, 5_000)
        assertEquals("关闭账户不能隐藏", assertFailsWith<IllegalArgumentException> { useCase(accountId, false) }.message)
        assertEquals("账户不存在", assertFailsWith<IllegalArgumentException> { useCase(999, true) }.message)
    }

    @Test
    fun reopenClearsOnlyClosedAtAndAllowsLegacyNonzeroAccountToBeSettled() = runBlocking {
        val fixture = Fixture(now = 8_000)
        val accountId = fixture.accounts.createAccount(
            Account(
                name = "迁移账户",
                initialBalance = 100,
                createdAt = 1,
                isHidden = true,
                displayOrder = 7,
                closedAt = 5_000,
            ),
        )
        val reminderId = fixture.reminders.insertReminder(reminder(accountId, enabled = false, updatedAt = 5_000))

        ReopenAccountUseCase(fixture.accounts, fixture.transactions)(accountId)
        CreateBalanceAdjustmentUseCase(
            fixture.accounts,
            fixture.transactions,
            RefreshAccountActivityStateUseCase(fixture.accounts, fixture.transactions),
            fixture.clock,
        )(
            accountId = accountId,
            delta = -100,
            occurredAt = 7_000,
            operationId = "settle-legacy",
        )

        val reopened = requireNotNull(fixture.accounts.getAccountById(accountId))
        assertNull(reopened.closedAt)
        assertTrue(reopened.isHidden)
        assertEquals(7, reopened.displayOrder)
        assertFalse(requireNotNull(fixture.reminders.getReminderById(reminderId)).isEnabled)
        assertEquals(0, fixture.balance(accountId))
    }

    @Test
    fun closureIssuesEmitOnlyNonzeroClosedAccountsAndReactToLedgerAndReopen() = runBlocking {
        val fixture = Fixture(now = 8_000)
        val zeroId = fixture.createAccount("零余额关闭", initialBalance = 0)
        val nonzeroId = fixture.createAccount("需处理", initialBalance = 100)
        fixture.accounts.closeAccount(zeroId, 6_000)
        fixture.accounts.closeAccount(nonzeroId, 7_000)
        val observe = ObserveAccountClosureIssuesUseCase(
            fixture.accounts,
            fixture.transactions,
            fixture.calculateBalance,
        )

        observe().test {
            assertEquals(
                listOf("需处理" to 100L),
                awaitItem().map { it.accountName to it.balance },
            )
            fixture.transactions.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = nonzeroId,
                    delta = -100,
                    occurredAt = 7_500,
                    createdAt = 7_500,
                    updatedAt = 7_500,
                    operationId = "fix-closed",
                ),
            )
            assertTrue(awaitItem().isEmpty())
            fixture.accounts.reopenAccount(nonzeroId)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Fixture(now: Long = 10_000) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val reminders = InMemoryRecurringReminderRepository(MutableStateFlow(now))
        val clock = CountingClock(now)
        val calculateBalance = CalculateCurrentBalanceUseCase(accounts, transactions, clock)
        private val close = CloseAccountUseCase(
            accounts,
            reminders,
            calculateBalance,
            transactions,
            clock,
        )

        suspend fun createAccount(name: String, initialBalance: Long): Long = accounts.createAccount(
            Account(name = name, initialBalance = initialBalance, createdAt = 1, lastUsedAt = 1),
        )

        suspend fun closeAccount(accountId: Long) = close(accountId)

        suspend fun balance(accountId: Long): Long = calculateBalance(accountId, clock.now)
    }

    private class CountingClock(val now: Long) : ClockProvider {
        var calls: Int = 0
            private set

        override fun nowMillis(): Long {
            calls += 1
            return now
        }
    }

    private fun reminder(accountId: Long, enabled: Boolean, updatedAt: Long) = RecurringReminder(
        name = "提醒-$accountId-$updatedAt",
        type = ReminderType.MANUAL.value,
        accountId = accountId,
        direction = CashFlowDirection.OUTFLOW.value,
        amount = 10,
        periodType = ReminderPeriodType.CUSTOM_DAYS.value,
        periodValue = 1,
        periodMonth = null,
        isEnabled = enabled,
        nextDueAt = 20_000,
        anchorDueAt = 20_000,
        createdAt = 1,
        updatedAt = updatedAt,
    )

    private fun cashFlow(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        operationId: String,
    ) = CashFlowRecord(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        note = "",
        occurredAt = 2,
        createdAt = 2,
        updatedAt = 2,
        operationId = operationId,
    )
}
