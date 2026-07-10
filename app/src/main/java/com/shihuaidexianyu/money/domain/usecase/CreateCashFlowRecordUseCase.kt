package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection

class CreateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
) {
    suspend operator fun invoke(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
    ): Long {
        require(amount > 0) { "金额必须大于 0" }
        require(occurredAt <= System.currentTimeMillis()) { "时间不能晚于当前时间" }
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        account.requireActiveForMutation("记录收支")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        val now = System.currentTimeMillis()
        val operationId = newLedgerOperationId()
        val recordId = transactionRepository.runInTransaction {
            val id = transactionRepository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = direction.value,
                    amount = amount,
                    note = note.trim(),
                    occurredAt = occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    operationId = operationId,
                ),
            )
            refreshAccountActivityStateUseCase(accountId)
            id
        }
        return recordId
    }
}
