package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CreateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
        operationId: String,
    ): LedgerInsertResult {
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        require(operationId.isNotBlank()) { "操作标识不能为空" }

        return transactionRepository.runInTransaction {
            val requested = TransferRecord(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                note = note.trim(),
                occurredAt = occurredAt,
                createdAt = 0L,
                updatedAt = 0L,
                operationId = operationId,
            )
            if (transactionRepository.queryTransferRecordByOperationId(operationId) != null) {
                return@runInTransaction transactionRepository.insertTransferRecord(requested)
            }

            val now = clockProvider.nowMillis()
            require(occurredAt <= now) { "时间不能晚于当前时间" }
            val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户不存在" }
            val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户不存在" }
            fromAccount.requireOpenForMutation("记录转账")
            toAccount.requireOpenForMutation("记录转账")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(fromAccount, occurredAt)
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(toAccount, occurredAt)

            transactionRepository.insertTransferRecord(
                requested.copy(createdAt = now, updatedAt = now),
            ).also { result ->
                if (result.inserted) {
                    refreshAccountActivityStateUseCase(fromAccountId)
                    refreshAccountActivityStateUseCase(toAccountId)
                }
            }
        }
    }
}
