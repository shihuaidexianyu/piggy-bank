package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class CreateTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
    ): Long {
        require(fromAccountId != toAccountId) { "请选择不同的转出和转入账户" }
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        val fromAccount = requireNotNull(accountRepository.getAccountById(fromAccountId)) { "转出账户不存在" }
        val toAccount = requireNotNull(accountRepository.getAccountById(toAccountId)) { "转入账户不存在" }
        fromAccount.requireActiveForMutation("记录转账")
        toAccount.requireActiveForMutation("记录转账")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(fromAccount, occurredAt)
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(toAccount, occurredAt)

        val now = System.currentTimeMillis()
        val recordId = transactionRepository.runInTransaction {
            transactionRepository.insertTransferRecord(
                TransferRecord(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = amount,
                    note = note.trim(),
                    occurredAt = occurredAt,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        refreshAccountActivityStateUseCase(fromAccountId)
        refreshAccountActivityStateUseCase(toAccountId)
        return recordId
    }
}
