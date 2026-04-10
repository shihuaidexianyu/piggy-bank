package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteBalanceUpdateRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.getBalanceUpdateRecordById(recordId) ?: return
        transactionRepository.runInTransaction {
            transactionRepository.deleteBalanceAdjustmentBySourceUpdateRecordId(recordId)
            transactionRepository.deleteBalanceUpdateRecord(recordId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}
