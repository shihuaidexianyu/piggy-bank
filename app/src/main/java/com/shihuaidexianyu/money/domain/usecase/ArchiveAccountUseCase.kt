package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class ArchiveAccountUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(accountId: Long, archivedAt: Long = System.currentTimeMillis()) {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        require(!account.isClosed) { "账户已归档" }
        transactionRepository.runInTransaction {
            accountRepository.archiveAccount(accountId, archivedAt)
            reminderRepository.queryAll()
                .filter { it.accountId == accountId && it.isEnabled }
                .forEach { reminder ->
                    reminderRepository.updateReminder(
                        reminder.copy(
                            isEnabled = false,
                            updatedAt = archivedAt,
                        ),
                    )
                }
        }
    }
}
