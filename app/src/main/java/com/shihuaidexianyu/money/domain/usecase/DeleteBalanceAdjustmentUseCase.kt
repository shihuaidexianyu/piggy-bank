package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.getBalanceAdjustmentRecordById(recordId) ?: return
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("删除余额调整")
        transactionRepository.runInTransaction {
            transactionRepository.deleteBalanceAdjustmentRecord(recordId, System.currentTimeMillis())
            refreshAccountActivityStateUseCase(existing.accountId)
        }
    }
}
