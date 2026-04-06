package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class DeleteReminderUseCase(
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(reminderId: Long) {
        reminderRepository.deleteReminder(reminderId)
    }
}
