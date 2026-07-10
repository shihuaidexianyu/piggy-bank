package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class ProcessDueReminderUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        reminderId: Long,
        expectedDueAt: Long,
        occurredAt: Long,
        amount: Long,
        note: String,
    ): Long {
        require(amount > 0) { "金额必须大于 0" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }
        val operationId = "cash:reminder:$reminderId:$expectedDueAt"

        return transactionRepository.runInTransaction {
            transactionRepository.queryCashFlowRecordByOperationId(operationId)?.let { existing ->
                return@runInTransaction transactionRepository.insertCashFlowRecord(
                    CashFlowRecord(
                        accountId = existing.accountId,
                        direction = existing.direction,
                        amount = amount,
                        note = note.trim(),
                        occurredAt = occurredAt,
                        createdAt = now,
                        updatedAt = now,
                        operationId = operationId,
                    ),
                ).recordId
            }

            val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
            check(reminder.isEnabled && reminder.nextDueAt == expectedDueAt) { "提醒状态已变化" }
            val account = requireNotNull(accountRepository.getAccountById(reminder.accountId)) { "账户不存在" }
            account.requireActiveForMutation("处理提醒")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            val insertResult = transactionRepository.insertCashFlowRecord(
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
            if (insertResult.inserted) {
                check(
                    reminderRepository.advanceOccurrence(
                        reminderId = reminderId,
                        expectedDueAt = expectedDueAt,
                        nextDueAt = reminder.nextDueAfter(now),
                        confirmedAt = now,
                        updatedAt = now,
                    ),
                ) { "提醒状态已变化" }
                refreshAccountActivityStateUseCase(reminder.accountId)
            }
            insertResult.recordId
        }
    }
}
