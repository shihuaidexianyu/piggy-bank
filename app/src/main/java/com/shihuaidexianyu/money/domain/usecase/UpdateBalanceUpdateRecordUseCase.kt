package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class UpdateBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        recordId: Long,
        actualBalance: Long,
        occurredAt: Long,
    ) {
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val existing = requireNotNull(transactionRepository.getBalanceUpdateRecordById(recordId)) { "余额核对记录不存在" }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("修改余额核对")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
        val context = resolveBalanceUpdateContextUseCase(
            accountId = existing.accountId,
            occurredAt = occurredAt,
            excludingRecordId = recordId,
        )
        transactionRepository.runInTransaction {
            transactionRepository.updateBalanceUpdateRecord(
                existing.copy(
                    actualBalance = actualBalance,
                    systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                    delta = actualBalance - context.systemBalanceBeforeUpdate,
                    occurredAt = occurredAt,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            refreshAccountActivityStateUseCase(existing.accountId)
        }
    }
}
