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

        val startInclusive = LedgerBalanceCalculator.openingAt(account)
        val endExclusive = LedgerBalanceCalculator.endExclusiveAfter(occurredAt)

        val inflow = transactionRepository.sumInflowBetween(accountId, startInclusive, endExclusive)
        val outflow = transactionRepository.sumOutflowBetween(accountId, startInclusive, endExclusive)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, startInclusive, endExclusive)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, startInclusive, endExclusive)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, startInclusive, endExclusive)
        val reconciliation = LedgerBalanceCalculator.reconciliationDeltaFromRecordsBefore(
            account = account,
            balanceUpdates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId),
            endExclusive = endExclusive,
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
