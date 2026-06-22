package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class UpdateBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        recordId: Long,
        delta: Long,
        occurredAt: Long,
    ) {
        require(delta != 0L) { "调整金额不能为 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val existing = requireNotNull(transactionRepository.getBalanceAdjustmentRecordById(recordId)) { "余额调整记录不存在" }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("修改余额调整")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        transactionRepository.runInTransaction {
            transactionRepository.updateBalanceAdjustmentRecord(
                existing.copy(delta = delta, occurredAt = occurredAt),
            )
            refreshAccountActivityStateUseCase(existing.accountId)
        }
    }
}
