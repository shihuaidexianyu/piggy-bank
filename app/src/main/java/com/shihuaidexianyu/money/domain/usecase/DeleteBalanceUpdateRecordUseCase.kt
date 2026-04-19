package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val recalculateBalanceUpdateChainUseCase: RecalculateBalanceUpdateChainUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.getBalanceUpdateRecordById(recordId) ?: return
        requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        transactionRepository.runInTransaction {
            transactionRepository.deleteBalanceAdjustmentBySourceUpdateRecordId(recordId)
            transactionRepository.deleteBalanceUpdateRecord(recordId)
            recalculateBalanceUpdateChainUseCase(existing.accountId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}
