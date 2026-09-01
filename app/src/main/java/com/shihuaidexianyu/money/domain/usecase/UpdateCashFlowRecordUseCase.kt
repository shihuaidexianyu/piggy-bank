package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class UpdateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        recordId: Long,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
        preserveNoteVerbatim: Boolean = false,
        expectedUpdatedAt: Long? = null,
    ) {
        require(amount > 0) { ValidationErrorText.AMOUNT_MUST_BE_POSITIVE }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { ValidationErrorText.OCCURRED_AT_IN_FUTURE }
        transactionRepository.runInTransaction {
            val existing = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId)) {
                "记录${ValidationErrorText.NOT_FOUND_SUFFIX}或已删除"
            }
            if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
                throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, recordId)
            }
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            val existingAccount = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            account.requireOpenForMutation("修改收支记录")
            existingAccount.requireOpenForMutation("修改收支记录")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
            val updated = existing.copy(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = if (preserveNoteVerbatim) note else note.trim(),
                occurredAt = occurredAt,
                updatedAt = nextMutationTimestamp(now, existing.updatedAt),
            )
            if (!transactionRepository.updateCashFlowRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, recordId)
            }
            appendSyncChangesUseCase?.upsertCashFlow(updated)
            setOf(existing.accountId, accountId).forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

