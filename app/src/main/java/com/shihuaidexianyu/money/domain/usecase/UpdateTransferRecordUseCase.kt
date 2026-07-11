package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider

class UpdateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
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
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }
        transactionRepository.runInTransaction {
            val existing = requireNotNull(transactionRepository.queryTransferRecordById(recordId)) {
                "记录不存在或已删除"
            }
            if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
                throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
            }
            val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户不存在" }
            val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户不存在" }
            val existingFromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) {
                "转出账户不存在"
            }
            val existingToAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) {
                "转入账户不存在"
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
            setOf(existing.fromAccountId, existing.toAccountId, fromAccountId, toAccountId).forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

