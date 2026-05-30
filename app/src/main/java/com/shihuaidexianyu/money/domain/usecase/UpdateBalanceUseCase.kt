package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

data class UpdateBalanceResult(
    val accountId: Long,
    val accountName: String,
    val systemBalanceBeforeUpdate: Long,
    val actualBalance: Long,
    val delta: Long,
)

class UpdateBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val recalculateBalanceUpdateChainUseCase: RecalculateBalanceUpdateChainUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        actualBalance: Long,
        occurredAt: Long,
    ): UpdateBalanceResult {
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }

        val account = requireNotNull(accountRepository.getAccountById(accountId))
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
        val context = resolveBalanceUpdateContextUseCase(accountId, occurredAt)
        val systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate
        val delta = actualBalance - systemBalanceBeforeUpdate
        val now = System.currentTimeMillis()

        transactionRepository.runInTransaction {
            transactionRepository.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = accountId,
                    actualBalance = actualBalance,
                    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
                    delta = delta,
                    occurredAt = occurredAt,
                    createdAt = now,
                ),
            )
            recalculateBalanceUpdateChainUseCase(accountId)
        }

        refreshAccountActivityStateUseCase(accountId)

        return UpdateBalanceResult(
            accountId = accountId,
            accountName = account.name,
            systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
            actualBalance = actualBalance,
            delta = delta,
        )
    }
}
