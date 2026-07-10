package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner

class SetAccountHiddenUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRunner: DatabaseTransactionRunner,
) {
    suspend operator fun invoke(accountId: Long, hidden: Boolean) {
        transactionRunner.runInTransaction {
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireOpenForMutation("隐藏")
            if (account.isHidden != hidden) {
                accountRepository.setHidden(accountId, hidden)
            }
        }
    }
}
