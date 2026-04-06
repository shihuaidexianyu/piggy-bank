package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class ConfirmReminderUseCase(
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(reminderId: Long) {
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        val now = System.currentTimeMillis()
        val nextDueAt = ReminderNextDueCalculator.calculateNextDue(
            currentDueAt = reminder.nextDueAt,
            periodType = ReminderPeriodType.fromValue(reminder.periodType),
            periodValue = reminder.periodValue,
            periodMonth = reminder.periodMonth,
        )
        reminderRepository.updateReminder(
            reminder.copy(
                nextDueAt = nextDueAt,
                lastConfirmedAt = now,
                updatedAt = now,
            ),
        )
    }
}
