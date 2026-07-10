package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider

class UpdateBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        recordId: Long,
        delta: Long,
        occurredAt: Long,
    ) {
        require(delta != 0L) { "调整金额不能为 0" }
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }

        val existing = requireNotNull(transactionRepository.getBalanceAdjustmentRecordById(recordId)) { "余额调整记录不存在" }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireActiveForMutation("修改余额调整")
        AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)

        transactionRepository.runInTransaction {
            val updated = existing.copy(
                delta = delta,
                occurredAt = occurredAt,
                updatedAt = nextLedgerMutationTimestamp(now, existing.updatedAt),
            )
            if (!transactionRepository.updateBalanceAdjustmentRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_ADJUSTMENT, recordId)
            }
            refreshAccountActivityStateUseCase(existing.accountId)
        }
    }
}
