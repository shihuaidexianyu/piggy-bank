package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.getBalanceUpdateRecordById(recordId) ?: return
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("删除余额核对")
        transactionRepository.runInTransaction {
            transactionRepository.deleteBalanceUpdateRecord(recordId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}
