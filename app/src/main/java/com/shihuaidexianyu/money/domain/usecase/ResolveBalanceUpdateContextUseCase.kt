package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

data class BalanceUpdateContext(
    val systemBalanceBeforeUpdate: Long,
)

class ResolveBalanceUpdateContextUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        occurredAt: Long,
        excludingRecordId: Long? = null,
    ): BalanceUpdateContext {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        if (!LedgerBalanceCalculator.isOpenAt(account, occurredAt)) {
            return BalanceUpdateContext(
                systemBalanceBeforeUpdate = 0L,
            )
        }

        val startAt = LedgerBalanceCalculator.startBeforeOpening(account)

        val inflow = transactionRepository.sumInflowBetween(accountId, startAt, occurredAt)
        val outflow = transactionRepository.sumOutflowBetween(accountId, startAt, occurredAt)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, startAt, occurredAt)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, startAt, occurredAt)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, startAt, occurredAt)
        val reconciliation = LedgerBalanceCalculator.reconciliationDeltaFromRecords(
            account = account,
            balanceUpdates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId),
            atTimeMillis = occurredAt,
            excludingBalanceUpdateId = excludingRecordId,
        )

        return BalanceUpdateContext(
            systemBalanceBeforeUpdate = LedgerBalanceCalculator.balanceAt(
                account = account,
                atTimeMillis = occurredAt,
                deltas = LedgerBalanceDeltas(
                    inflow = inflow,
                    outflow = outflow,
                    transferIn = transferIn,
                    transferOut = transferOut,
                    manualAdjustment = adjustment,
                    reconciliation = reconciliation,
                ),
            ),
        )
    }
}
