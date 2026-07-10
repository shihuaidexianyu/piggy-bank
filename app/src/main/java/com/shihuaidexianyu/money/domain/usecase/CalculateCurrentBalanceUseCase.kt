package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CalculateCurrentBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accountId: Long,
        atTimeMillis: Long = clockProvider.nowMillis(),
    ): Long {
        return before(accountId, LedgerBalanceCalculator.endExclusiveAfter(atTimeMillis))
    }

    suspend fun before(accountId: Long, endExclusive: Long): Long {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        val startInclusive = LedgerBalanceCalculator.openingAt(account)
        if (endExclusive <= startInclusive) return 0L

        val inflow = transactionRepository.sumInflowBetween(accountId, startInclusive, endExclusive)
        val outflow = transactionRepository.sumOutflowBetween(accountId, startInclusive, endExclusive)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, startInclusive, endExclusive)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, startInclusive, endExclusive)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, startInclusive, endExclusive)
        val reconciliation = transactionRepository
            .queryBalanceUpdateRecordsBetween(startInclusive, endExclusive)
            .asSequence()
            .filter { it.accountId == accountId }
            .sumOf { it.delta }

        return account.initialBalance + LedgerBalanceDeltas(
            inflow = inflow,
            outflow = outflow,
            transferIn = transferIn,
            transferOut = transferOut,
            manualAdjustment = adjustment,
            reconciliation = reconciliation,
        ).net
    }
}
