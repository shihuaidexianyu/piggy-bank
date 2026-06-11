package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class CalculateCurrentBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        atTimeMillis: Long = Long.MAX_VALUE,
    ): Long {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        if (!LedgerBalanceCalculator.isOpenAt(account, atTimeMillis)) return 0L

        val startAt = LedgerBalanceCalculator.startBeforeOpening(account)

        val inflow = transactionRepository.sumInflowBetween(accountId, startAt, atTimeMillis)
        val outflow = transactionRepository.sumOutflowBetween(accountId, startAt, atTimeMillis)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, startAt, atTimeMillis)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, startAt, atTimeMillis)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, startAt, atTimeMillis)
        val reconciliation = LedgerBalanceCalculator.reconciliationDeltaFromRecords(
            account = account,
            balanceUpdates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId),
            atTimeMillis = atTimeMillis,
        )

        return LedgerBalanceCalculator.balanceAt(
            account = account,
            atTimeMillis = atTimeMillis,
            deltas = LedgerBalanceDeltas(
                inflow = inflow,
                outflow = outflow,
                transferIn = transferIn,
                transferOut = transferOut,
                manualAdjustment = adjustment,
                reconciliation = reconciliation,
            ),
        )
    }
}
