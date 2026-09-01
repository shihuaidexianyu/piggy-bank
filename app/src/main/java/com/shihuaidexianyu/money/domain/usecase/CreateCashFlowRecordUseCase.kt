package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class CreateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
        operationId: String,
    ): LedgerInsertResult {
        require(amount > 0) { ValidationErrorText.AMOUNT_MUST_BE_POSITIVE }
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
            require(occurredAt <= now) { ValidationErrorText.OCCURRED_AT_IN_FUTURE }
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            account.requireOpenForMutation("记录收支")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

            val stored = requested.copy(createdAt = now, updatedAt = now)
            transactionRepository.insertCashFlowRecord(stored).also { result ->
                if (result.inserted) {
                    appendSyncChangesUseCase?.upsertCashFlow(stored.copy(id = result.recordId))
                    refreshAccountActivityStateUseCase(accountId)
                }
            }
        }
    }
}
