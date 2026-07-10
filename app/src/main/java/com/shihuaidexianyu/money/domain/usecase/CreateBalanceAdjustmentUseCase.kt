package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CreateBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accountId: Long,
        delta: Long,
        occurredAt: Long,
        operationId: String,
    ): LedgerInsertResult {
        require(delta != 0L) { "调整金额不能为 0" }
        require(operationId.isNotBlank()) { "操作标识不能为空" }

        return transactionRepository.runInTransaction {
            val requested = BalanceAdjustmentRecord(
                accountId = accountId,
                delta = delta,
                occurredAt = occurredAt,
                createdAt = 0L,
                updatedAt = 0L,
                operationId = operationId,
            )
            if (transactionRepository.queryBalanceAdjustmentRecordByOperationId(operationId) != null) {
                return@runInTransaction transactionRepository.insertBalanceAdjustmentRecord(requested)
            }

            val now = clockProvider.nowMillis()
            require(occurredAt <= now) { "时间不能晚于当前时间" }
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireActiveForMutation("新建余额调整")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            transactionRepository.insertBalanceAdjustmentRecord(
                requested.copy(createdAt = now, updatedAt = now),
            ).also { result ->
                if (result.inserted) {
                    refreshAccountActivityStateUseCase(accountId)
                }
            }
        }
    }
}
