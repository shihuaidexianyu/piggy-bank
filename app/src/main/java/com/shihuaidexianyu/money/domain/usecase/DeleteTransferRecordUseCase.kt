package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.queryTransferRecordById(recordId) ?: return
        val fromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) { "转出账户不存在" }
        val toAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) { "转入账户不存在" }
        fromAccount.requireActiveForMutation("删除转账记录")
        toAccount.requireActiveForMutation("删除转账记录")
        val affectedAccountIds = setOf(existing.fromAccountId, existing.toAccountId)
        transactionRepository.runInTransaction {
            transactionRepository.softDeleteTransferRecord(recordId, System.currentTimeMillis())
            affectedAccountIds.forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

