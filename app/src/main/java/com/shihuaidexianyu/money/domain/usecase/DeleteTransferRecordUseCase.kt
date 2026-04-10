package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteTransferRecordUseCase(
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.queryTransferRecordById(recordId) ?: return
        transactionRepository.softDeleteTransferRecord(recordId, System.currentTimeMillis())
        refreshAccountActivityStateUseCase(existing.fromAccountId)
        refreshAccountActivityStateUseCase(existing.toAccountId)
    }
}

