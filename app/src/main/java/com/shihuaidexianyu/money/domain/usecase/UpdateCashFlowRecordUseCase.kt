package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider

class UpdateCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        recordId: Long,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
    ) {
        require(amount > 0) { "金额必须大于 0" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        account.requireActiveForMutation("修改收支记录")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        val existing = requireNotNull(transactionRepository.queryCashFlowRecordById(recordId)) { "记录不存在或已删除" }
        val existingAccount = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        existingAccount.requireActiveForMutation("修改收支记录")
        val updated = existing.copy(
            accountId = accountId,
            direction = direction.value,
            amount = amount,
            note = note.trim(),
            occurredAt = occurredAt,
            updatedAt = nextLedgerMutationTimestamp(now, existing.updatedAt),
        )
        val affectedAccountIds = setOf(existing.accountId, accountId)
        transactionRepository.runInTransaction {
            if (!transactionRepository.updateCashFlowRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, recordId)
            }
            affectedAccountIds.forEach {
                refreshAccountActivityStateUseCase(it)
            }
        }
    }
}

