package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester

class UpdateReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
) {
    suspend operator fun invoke(
        reminderId: Long,
        name: String,
        type: ReminderType,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
        isEnabled: Boolean,
    ) {
        require(name.isNotBlank()) { "名称不能为空" }
        require(amount > 0) { "金额必须大于 0" }
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        account.requireOpenForMutation("修改提醒")
        ReminderScheduleValidator.validate(periodType, periodValue, periodMonth)

        val existing = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        val existingAccount = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        existingAccount.requireOpenForMutation("修改提醒")

        val periodChanged = existing.periodType != periodType.value ||
            existing.periodValue != periodValue ||
            existing.periodMonth != periodMonth

        val nextDueAt = if (periodChanged) {
            ReminderNextDueCalculator.calculateFirstDue(periodType, periodValue, periodMonth)
        } else {
            existing.nextDueAt
        }

        reminderRepository.updateReminder(
            existing.copy(
                name = name.trim(),
                type = type.value,
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                periodType = periodType.value,
                periodValue = periodValue,
                periodMonth = periodMonth,
                isEnabled = isEnabled,
                nextDueAt = nextDueAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_CHANGED) }
    }
}
