package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class ProcessDueReminderUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        reminderId: Long,
        occurredAt: Long,
        amount: Long,
        note: String,
    ): Long {
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        val account = requireNotNull(accountRepository.getAccountById(reminder.accountId)) { "账户不存在" }
        account.requireActiveForMutation("处理提醒")
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        val now = System.currentTimeMillis()
        val operationId = newLedgerOperationId()
        val recordId = transactionRepository.runInTransaction {
            val insertedId = transactionRepository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = reminder.accountId,
                    direction = reminder.direction,
                    amount = amount,
                    note = note.trim(),
                    occurredAt = occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    operationId = operationId,
                ),
            )
            reminderRepository.updateReminder(
                reminder.copy(
                    nextDueAt = reminder.nextDueAfter(now),
                    lastConfirmedAt = now,
                    updatedAt = now,
                ),
            )
            refreshAccountActivityStateUseCase(reminder.accountId)
            insertedId
        }
        return recordId
    }
}
