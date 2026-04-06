package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class UpdateReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
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
        requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }

        val existing = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }

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
    }
}
