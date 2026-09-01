package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class CreateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
        operationId: String,
    ): LedgerInsertResult {
        require(fromAccountId != toAccountId) { ValidationErrorText.SAME_TRANSFER_ACCOUNTS }
        require(amount > 0) { ValidationErrorText.AMOUNT_MUST_BE_POSITIVE }
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
            require(occurredAt <= now) { ValidationErrorText.OCCURRED_AT_IN_FUTURE }
            val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            fromAccount.requireOpenForMutation("记录转账")
            toAccount.requireOpenForMutation("记录转账")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(fromAccount, occurredAt)
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(toAccount, occurredAt)

            val stored = requested.copy(createdAt = now, updatedAt = now)
            transactionRepository.insertTransferRecord(stored).also { result ->
                if (result.inserted) {
                    appendSyncChangesUseCase?.upsertTransfer(stored.copy(id = result.recordId))
                    refreshAccountActivityStateUseCase(fromAccountId)
                    refreshAccountActivityStateUseCase(toAccountId)
                }
            }
        }
    }
}
