package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider

class UpdateBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        recordId: Long,
        actualBalance: Long,
        occurredAt: Long,
    ) {
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { "时间不能晚于当前时间" }

        transactionRepository.runInTransaction {
            val existing = requireNotNull(transactionRepository.getBalanceUpdateRecordById(recordId)) {
                "余额核对记录不存在"
            }
            val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
            account.requireOpenForMutation("修改余额核对")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
            val context = resolveBalanceUpdateContextUseCase(
                accountId = existing.accountId,
                occurredAt = occurredAt,
                excludingRecordId = recordId,
            )
            val updated = existing.copy(
                actualBalance = actualBalance,
                systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                delta = actualBalance - context.systemBalanceBeforeUpdate,
                occurredAt = occurredAt,
                updatedAt = nextMutationTimestamp(now, existing.updatedAt),
            )
            if (!transactionRepository.updateBalanceUpdateRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, recordId)
            }
            refreshAccountActivityStateUseCase(existing.accountId)
        }
    }
}
