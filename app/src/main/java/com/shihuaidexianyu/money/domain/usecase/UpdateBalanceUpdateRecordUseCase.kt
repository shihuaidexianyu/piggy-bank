package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class UpdateBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val recalculateBalanceUpdateChainUseCase: RecalculateBalanceUpdateChainUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        recordId: Long,
        actualBalance: Long,
        occurredAt: Long,
    ) {
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val existing = requireNotNull(transactionRepository.getBalanceUpdateRecordById(recordId))
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId))
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
        val context = resolveBalanceUpdateContextUseCase(
            accountId = existing.accountId,
            occurredAt = occurredAt,
            excludingRecordId = recordId,
        )
        transactionRepository.runInTransaction {
            transactionRepository.deleteBalanceAdjustmentBySourceUpdateRecordId(recordId)
            transactionRepository.updateBalanceUpdateRecord(
                existing.copy(
                    actualBalance = actualBalance,
                    systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                    delta = actualBalance - context.systemBalanceBeforeUpdate,
                    occurredAt = occurredAt,
                ),
            )
            recalculateBalanceUpdateChainUseCase(existing.accountId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
    }
}
