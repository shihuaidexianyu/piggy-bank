package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository

class DeleteReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(reminderId: Long) {
        val reminder = reminderRepository.getReminderById(reminderId) ?: return
        val account = requireNotNull(accountRepository.getAccountById(reminder.accountId)) { "账户不存在" }
        account.requireOpenForMutation("删除提醒")
        reminderRepository.deleteReminder(reminderId)
    }
}
