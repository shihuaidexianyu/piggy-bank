package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class ConfirmReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(reminderId: Long) {
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        val account = requireNotNull(accountRepository.getAccountById(reminder.accountId)) { "账户不存在" }
        account.requireActiveForMutation("确认提醒")
        val now = System.currentTimeMillis()
        reminderRepository.updateReminder(
            reminder.copy(
                nextDueAt = reminder.nextDueAfter(now),
                lastConfirmedAt = now,
                updatedAt = now,
            ),
        )
    }
}

internal fun RecurringReminder.nextDueAfter(now: Long): Long {
    val periodType = ReminderPeriodType.fromValue(periodType)
    var nextDueAt = this.nextDueAt
    do {
        val previousDueAt = nextDueAt
        nextDueAt = ReminderNextDueCalculator.calculateNextDue(
            currentDueAt = previousDueAt,
            periodType = periodType,
            periodValue = periodValue,
            periodMonth = periodMonth,
        )
        if (nextDueAt <= previousDueAt) {
            // 计算异常时，使用一天后的时间作为安全降级，避免崩溃或死循环
            nextDueAt = previousDueAt + 24L * 60L * 60L * 1000L
            break
        }
    } while (nextDueAt <= now)
    return nextDueAt
}
