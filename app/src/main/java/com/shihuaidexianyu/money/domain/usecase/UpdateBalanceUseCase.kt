package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

data class UpdateBalanceResult(
    val insertResult: LedgerInsertResult,
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
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accountId: Long,
        actualBalance: Long,
        occurredAt: Long,
        operationId: String,
    ): UpdateBalanceResult {
        require(operationId.isNotBlank()) { "操作标识不能为空" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }

        return transactionRepository.runInTransaction {
            transactionRepository.queryBalanceUpdateRecordByOperationId(operationId)?.let { existing ->
                val replay = transactionRepository.insertBalanceUpdateRecord(
                    BalanceUpdateRecord(
                        accountId = accountId,
                        actualBalance = actualBalance,
                        systemBalanceBeforeUpdate = 0,
                        delta = 0,
                        occurredAt = occurredAt,
                        createdAt = now,
                        updatedAt = now,
                        operationId = operationId,
                    ),
                )
                val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
                return@runInTransaction existing.toResult(replay, account.name)
            }

            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireActiveForMutation("核对余额")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
            val context = resolveBalanceUpdateContextUseCase(accountId, occurredAt)
            val requested = BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = actualBalance,
                systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                delta = actualBalance - context.systemBalanceBeforeUpdate,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                operationId = operationId,
            )
            val insertResult = transactionRepository.insertBalanceUpdateRecord(requested)
            val stored = if (insertResult.inserted) {
                requested.copy(id = insertResult.recordId)
            } else {
                requireNotNull(transactionRepository.queryBalanceUpdateRecordByOperationId(operationId))
            }
            if (insertResult.inserted) {
                refreshAccountActivityStateUseCase(accountId)
            }
            stored.toResult(insertResult, account.name)
        }
    }

    private fun BalanceUpdateRecord.toResult(
        insertResult: LedgerInsertResult,
        accountName: String,
    ): UpdateBalanceResult {
        return UpdateBalanceResult(
            insertResult = insertResult,
            accountId = accountId,
            accountName = accountName,
            systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
            actualBalance = actualBalance,
            delta = delta,
        )
    }
}
