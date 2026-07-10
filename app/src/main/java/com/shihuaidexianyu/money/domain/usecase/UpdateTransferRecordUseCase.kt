package com.shihuaidexianyu.money.domain.usecase

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
    ) {
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }
        val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户不存在" }
        val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户不存在" }
        fromAccount.requireActiveForMutation("修改转账记录")
        toAccount.requireActiveForMutation("修改转账记录")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(fromAccount, occurredAt)
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(toAccount, occurredAt)

        val existing = requireNotNull(transactionRepository.queryTransferRecordById(recordId)) { "记录不存在或已删除" }
        val existingFromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) { "转出账户不存在" }
        val existingToAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) { "转入账户不存在" }
        existingFromAccount.requireActiveForMutation("修改转账记录")
        existingToAccount.requireActiveForMutation("修改转账记录")
        val updated = existing.copy(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            note = note.trim(),
            occurredAt = occurredAt,
            updatedAt = nextLedgerMutationTimestamp(now, existing.updatedAt),
        )
        val affectedAccountIds = setOf(existing.fromAccountId, existing.toAccountId, fromAccountId, toAccountId)
        transactionRepository.runInTransaction {
            if (!transactionRepository.updateTransferRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
            }
            affectedAccountIds.forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

