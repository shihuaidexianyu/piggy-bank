package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection

class CreateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        purpose: String,
        occurredAt: Long,
    ): Long {
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        requireNotNull(accountRepository.getAccountById(accountId))

        val now = System.currentTimeMillis()
        val recordId = transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                purpose = purpose.trim(),
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        refreshAccountActivityStateUseCase(accountId)
        return recordId
    }
}
