package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class DeleteCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(recordId: Long) {
        val existing = transactionRepository.queryCashFlowRecordById(recordId) ?: return
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("删除收支记录")
        transactionRepository.runInTransaction {
            transactionRepository.softDeleteCashFlowRecord(recordId, System.currentTimeMillis())
        }
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}

