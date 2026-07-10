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
        if (atTimeMillis == Long.MAX_VALUE) return at(accountId, atTimeMillis)
        return before(accountId, LedgerBalanceCalculator.endExclusiveAfter(atTimeMillis))
    }

    private suspend fun at(accountId: Long, atTimeMillis: Long): Long {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        return LedgerBalanceCalculator.balanceAt(
            account = account,
            atTimeMillis = atTimeMillis,
            deltas = LedgerBalanceCalculator.deltasFromRecords(
                account = account,
                cashFlows = transactionRepository.queryAllActiveCashFlowRecords(),
                transfers = transactionRepository.queryAllActiveTransferRecords(),
                balanceUpdates = transactionRepository.queryAllBalanceUpdateRecords(),
                adjustments = transactionRepository.queryAllBalanceAdjustmentRecords(),
                atTimeMillis = atTimeMillis,
            ),
        )
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
