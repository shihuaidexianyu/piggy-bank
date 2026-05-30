package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import kotlinx.coroutines.flow.Flow

class ObserveDueRemindersUseCase(
    private val reminderRepository: RecurringReminderRepository,
) {
    operator fun invoke(): Flow<List<RecurringReminder>> =
        reminderRepository.observeDueReminders()
}
