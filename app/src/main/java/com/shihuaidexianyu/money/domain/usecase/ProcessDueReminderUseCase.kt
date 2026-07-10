package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
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
        accountId: Long,
        direction: CashFlowDirection,
        occurredAt: Long,
        amount: Long,
        note: String,
    ): Long {
        require(amount > 0) { "金额必须大于 0" }
        val operationId = "cash:reminder:$reminderId:$expectedDueAt"

        return transactionRepository.runInTransaction {
            val requested = CashFlowRecord(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = note.trim(),
                occurredAt = occurredAt,
                createdAt = 0L,
                updatedAt = 0L,
                operationId = operationId,
            )
            if (transactionRepository.queryCashFlowRecordByOperationId(operationId) != null) {
                return@runInTransaction transactionRepository.insertCashFlowRecord(requested).recordId
            }

            val now = clockProvider.nowMillis()
            require(occurredAt <= now) { "时间不能晚于当前时间" }
            val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
            check(reminder.isEnabled && reminder.nextDueAt == expectedDueAt) { "提醒状态已变化" }
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireActiveForMutation("处理提醒")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            val insertResult = transactionRepository.insertCashFlowRecord(
                requested.copy(createdAt = now, updatedAt = now),
            )
            if (insertResult.inserted) {
                check(
                    reminderRepository.advanceOccurrence(
                        reminderId = reminderId,
                        expectedDueAt = expectedDueAt,
                        nextDueAt = ReminderNextDueCalculator.calculateNextDue(
                            currentDueAt = expectedDueAt,
                            periodType = ReminderPeriodType.fromValue(reminder.periodType),
                            periodValue = reminder.periodValue,
                            periodMonth = reminder.periodMonth,
                        ),
                        confirmedAt = now,
                        updatedAt = now,
                    ),
                ) { "提醒状态已变化" }
                refreshAccountActivityStateUseCase(accountId)
            }
            insertResult.recordId
        }
    }
}
