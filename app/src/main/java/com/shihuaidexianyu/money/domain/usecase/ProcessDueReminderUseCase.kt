package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider

class ProcessDueReminderUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
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

        val recordId = transactionRepository.runInTransaction {
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
            account.requireOpenForMutation("处理提醒")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            val insertResult = transactionRepository.insertCashFlowRecord(
                requested.copy(createdAt = now, updatedAt = now),
            )
            if (insertResult.inserted) {
                check(
                    reminderRepository.advanceOccurrence(
                        reminderId = reminderId,
                        expectedDueAt = expectedDueAt,
                        expectedUpdatedAt = reminder.updatedAt,
                        nextDueAt = ReminderNextDueCalculator.calculateNextDue(
                            currentDueAt = expectedDueAt,
                            anchorDueAt = reminder.anchorDueAt,
                            periodType = ReminderPeriodType.fromValue(reminder.periodType),
                            periodValue = reminder.periodValue,
                            periodMonth = reminder.periodMonth,
                            zoneId = zoneIdProvider.zoneId(),
                        ),
                        confirmedAt = now,
                        updatedAt = com.shihuaidexianyu.money.domain.time.nextMutationTimestamp(
                            now,
                            reminder.updatedAt,
                        ),
                    ),
                ) { "提醒状态已变化" }
                refreshAccountActivityStateUseCase(accountId)
            }
            insertResult.recordId
        }
        runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_PROCESSED) }
        return recordId
    }
}
