package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class SetAccountHiddenUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRunner: DatabaseTransactionRunner,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(accountId: Long, hidden: Boolean) {
        transactionRunner.runInTransaction {
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            account.requireOpenForMutation("隐藏")
            if (account.isHidden != hidden) {
                accountRepository.setHidden(accountId, hidden)
                accountRepository.getAccountById(accountId)?.let { updated ->
                    appendSyncChangesUseCase?.upsertAccount(updated)
                }
            }
        }
    }
}
