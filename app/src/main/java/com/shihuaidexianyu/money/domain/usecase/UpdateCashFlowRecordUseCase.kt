package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection

class UpdateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        recordId: Long,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        purpose: String,
        occurredAt: Long,
    ) {
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        requireNotNull(accountRepository.getAccountById(accountId))

        val existing = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId)) { "记录不存在或已删除" }
        val updated = existing.copy(
            accountId = accountId,
            direction = direction.value,
            amount = amount,
            purpose = purpose.trim(),
            occurredAt = occurredAt,
            updatedAt = System.currentTimeMillis(),
        )
        transactionRepository.updateCashFlowRecord(updated)
        refreshAccountActivityStateUseCase(existing.accountId)
        if (existing.accountId != accountId) {
            refreshAccountActivityStateUseCase(accountId)
        }
    }
}

