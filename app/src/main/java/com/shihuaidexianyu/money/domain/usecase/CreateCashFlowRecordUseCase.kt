package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CreateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
        operationId: String,
    ): LedgerInsertResult {
        require(amount > 0) { "金额必须大于 0" }
        require(operationId.isNotBlank()) { "操作标识不能为空" }

        return transactionRepository.runInTransaction {
            val requested = CashFlowRecord(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = note.trim(),
                occurredAt = occurredAt,
                createdAt = 0L,
                updatedAt = 0L,
                operationId = operationId,
            )
            if (transactionRepository.queryCashFlowRecordByOperationId(operationId) != null) {
                return@runInTransaction transactionRepository.insertCashFlowRecord(requested)
            }

            val now = clockProvider.nowMillis()
            require(occurredAt <= now) { "时间不能晚于当前时间" }
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireActiveForMutation("记录收支")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            transactionRepository.insertCashFlowRecord(
                requested.copy(createdAt = now, updatedAt = now),
            ).also { result ->
                if (result.inserted) {
                    refreshAccountActivityStateUseCase(accountId)
                }
            }
        }
    }
}
