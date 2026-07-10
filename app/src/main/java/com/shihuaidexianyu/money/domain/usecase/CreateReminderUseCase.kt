package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class CreateReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(
        name: String,
        type: ReminderType,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
    ): Long {
        require(name.isNotBlank()) { "名称不能为空" }
        require(amount > 0) { "金额必须大于 0" }
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        account.requireOpenForMutation("创建提醒")
        ReminderScheduleValidator.validate(periodType, periodValue, periodMonth)

        val nextDueAt = ReminderNextDueCalculator.calculateFirstDue(
            periodType = periodType,
            periodValue = periodValue,
            periodMonth = periodMonth,
        )
        val now = System.currentTimeMillis()
        return reminderRepository.insertReminder(
            RecurringReminder(
                name = name.trim(),
                type = type.value,
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                periodType = periodType.value,
                periodValue = periodValue,
                periodMonth = periodMonth,
                isEnabled = true,
                nextDueAt = nextDueAt,
                anchorDueAt = nextDueAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
