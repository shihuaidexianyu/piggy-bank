package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.ArchiveAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ArchiveAccountUseCaseTest {
    @Test
    fun `archiving account disables recurring reminders and hides them from due list`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository(MutableStateFlow(1_000L))
        val accountId = accountRepository.createAccount(
            Account(name = "信用卡", initialBalance = 0, createdAt = 1L),
        )
        val reminderId = reminderRepository.insertReminder(
            reminder(accountId = accountId, nextDueAt = 500L),
        )

        assertEquals(listOf(reminderId), reminderRepository.observeDueReminders().first().map { it.id })

        ArchiveAccountUseCase(accountRepository, reminderRepository, transactionRepository)(
            accountId = accountId,
            archivedAt = 2_000L,
        )

        assertTrue(accountRepository.getAccountById(accountId)?.isClosed == true)
        assertTrue(reminderRepository.getReminderById(reminderId)?.isEnabled == false)
        assertTrue(reminderRepository.observeDueReminders().first().isEmpty())
    }

    @Test
    fun `archived account rejects money balance account and reminder mutations`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository(MutableStateFlow(1_000L))
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val archivedAccountId = accountRepository.createAccount(
            Account(name = "归档账户", initialBalance = 10_000, createdAt = 1L),
        )
        val activeAccountId = accountRepository.createAccount(
            Account(name = "活跃账户", initialBalance = 5_000, createdAt = 1L),
        )
        val cashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = archivedAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                note = "旧记录",
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
                operationId = testOperationId(),
            ),
        )
        val transferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = archivedAccountId,
                toAccountId = activeAccountId,
                amount = 100,
                note = "旧转账",
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
                operationId = testOperationId(),
            ),
        )
        val balanceUpdateId = transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = archivedAccountId,
                actualBalance = 10_000,
                systemBalanceBeforeUpdate = 10_000,
                delta = 0,
                occurredAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
                operationId = testOperationId(),
            ),
        )
        val reminderId = reminderRepository.insertReminder(
            reminder(accountId = archivedAccountId, nextDueAt = 500L),
        )
        ArchiveAccountUseCase(accountRepository, reminderRepository, transactionRepository)(
            accountId = archivedAccountId,
            archivedAt = 4L,
        )

        assertArchivedRejected {
            CreateCashFlowRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(
                accountId = archivedAccountId,
                direction = CashFlowDirection.INFLOW,
                amount = 100,
                note = "收入",
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            UpdateCashFlowRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(
                recordId = cashFlowId,
                accountId = archivedAccountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 50,
                note = "修改",
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            DeleteCashFlowRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(cashFlowId)
        }
        assertArchivedRejected {
            CreateTransferRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(
                fromAccountId = archivedAccountId,
                toAccountId = activeAccountId,
                amount = 100,
                note = "转账",
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            UpdateTransferRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(
                recordId = transferId,
                fromAccountId = activeAccountId,
                toAccountId = archivedAccountId,
                amount = 100,
                note = "修改",
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            DeleteTransferRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(transferId)
        }
        assertArchivedRejected {
            UpdateBalanceUseCase(
                accountRepository,
                transactionRepository,
                resolveContext,
                refreshActivity,
            )(
                accountId = archivedAccountId,
                actualBalance = 9_000,
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            UpdateBalanceUpdateRecordUseCase(
                accountRepository,
                transactionRepository,
                resolveContext,
                refreshActivity,
            )(
                recordId = balanceUpdateId,
                actualBalance = 9_000,
                occurredAt = 5L,
            )
        }
        assertArchivedRejected {
            DeleteBalanceUpdateRecordUseCase(
                accountRepository,
                transactionRepository,
                refreshActivity,
            )(balanceUpdateId)
        }
        assertArchivedRejected {
            UpdateAccountUseCase(
                accountRepository,
                accountReminderSettingsRepository,
            )(
                accountId = archivedAccountId,
                name = "新名称",
                balanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
                colorName = "blue",
            )
        }
        assertArchivedRejected {
            CreateReminderUseCase(accountRepository, reminderRepository)(
                name = "提醒",
                type = ReminderType.MANUAL,
                accountId = archivedAccountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 100,
                periodType = ReminderPeriodType.MONTHLY,
                periodValue = 1,
                periodMonth = null,
            )
        }
        assertArchivedRejected {
            UpdateReminderUseCase(accountRepository, reminderRepository)(
                reminderId = reminderId,
                name = "提醒",
                type = ReminderType.MANUAL,
                accountId = archivedAccountId,
                direction = CashFlowDirection.OUTFLOW,
                amount = 100,
                periodType = ReminderPeriodType.MONTHLY,
                periodValue = 1,
                periodMonth = null,
                isEnabled = true,
            )
        }
        assertArchivedRejected {
            DeleteReminderUseCase(accountRepository, reminderRepository)(reminderId)
        }
        assertArchivedRejected {
            ConfirmReminderUseCase(accountRepository, reminderRepository)(reminderId)
        }
    }

    private fun reminder(accountId: Long, nextDueAt: Long): RecurringReminder {
        return RecurringReminder(
            name = "订阅",
            type = ReminderType.SUBSCRIPTION.value,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW.value,
            amount = 100,
            periodType = ReminderPeriodType.CUSTOM_DAYS.value,
            periodValue = 30,
            periodMonth = null,
            nextDueAt = nextDueAt,
            createdAt = 1L,
            updatedAt = 1L,
            anchorDueAt = nextDueAt,
        )
    }

    private suspend fun assertArchivedRejected(block: suspend () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException> {
            block()
        }
        assertTrue(error.message?.startsWith("归档账户不能") == true)
    }
}
