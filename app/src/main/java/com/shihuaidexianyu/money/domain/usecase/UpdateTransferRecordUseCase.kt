package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class UpdateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        recordId: Long,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
    ) {
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        requireNotNull(accountRepository.getAccountById(fromAccountId))
        requireNotNull(accountRepository.getAccountById(toAccountId))

        val existing = requireNotNull(transactionRepository.queryTransferRecordById(recordId)) { "记录不存在或已删除" }
        val updated = existing.copy(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            note = note.trim(),
            occurredAt = occurredAt,
            updatedAt = System.currentTimeMillis(),
        )
        transactionRepository.updateTransferRecord(updated)
        setOf(existing.fromAccountId, existing.toAccountId, fromAccountId, toAccountId).forEach {
            refreshAccountActivityStateUseCase(it)
        }
    }
}

