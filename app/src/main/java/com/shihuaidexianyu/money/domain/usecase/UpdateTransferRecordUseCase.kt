package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class UpdateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        recordId: Long,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
        preserveNoteVerbatim: Boolean = false,
        expectedUpdatedAt: Long? = null,
    ) {
        require(fromAccountId != toAccountId) { ValidationErrorText.SAME_TRANSFER_ACCOUNTS }
        require(amount > 0) { ValidationErrorText.AMOUNT_MUST_BE_POSITIVE }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { ValidationErrorText.OCCURRED_AT_IN_FUTURE }
        transactionRepository.runInTransaction {
            val existing = requireNotNull(transactionRepository.queryTransferRecordById(recordId)) {
                "记录${ValidationErrorText.NOT_FOUND_SUFFIX}或已删除"
            }
            if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
                throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
            }
            val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            val existingFromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) {
                "转出账户${ValidationErrorText.NOT_FOUND_SUFFIX}"
            }
            val existingToAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) {
                "转入账户${ValidationErrorText.NOT_FOUND_SUFFIX}"
            }
            listOf(fromAccount, toAccount, existingFromAccount, existingToAccount).forEach {
                it.requireOpenForMutation("修改转账记录")
            }
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(fromAccount, occurredAt)
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(toAccount, occurredAt)
            val updated = existing.copy(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                note = if (preserveNoteVerbatim) note else note.trim(),
                occurredAt = occurredAt,
                updatedAt = nextMutationTimestamp(now, existing.updatedAt),
            )
            if (!transactionRepository.updateTransferRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
            }
            appendSyncChangesUseCase?.upsertTransfer(updated)
            setOf(existing.fromAccountId, existing.toAccountId, fromAccountId, toAccountId).forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

