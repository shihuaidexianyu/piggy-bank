package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class CreateBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        delta: Long,
        occurredAt: Long,
    ): Long {
        require(delta != 0L) { "调整金额不能为 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        account.requireActiveForMutation("新建余额调整")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        val now = System.currentTimeMillis()
        val recordId = transactionRepository.runInTransaction {
            val id = transactionRepository.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = accountId,
                    delta = delta,
                    occurredAt = occurredAt,
                    createdAt = now,
                ),
            )
            refreshAccountActivityStateUseCase(accountId)
            id
        }
        return recordId
    }
}
