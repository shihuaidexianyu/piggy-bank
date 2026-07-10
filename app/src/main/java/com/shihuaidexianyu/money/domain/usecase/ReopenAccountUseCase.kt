package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner

class ReopenAccountUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRunner: DatabaseTransactionRunner,
) {
    suspend operator fun invoke(accountId: Long) {
        transactionRunner.runInTransaction {
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            if (account.isClosed) {
                accountRepository.reopenAccount(accountId)
            }
        }
    }
}
