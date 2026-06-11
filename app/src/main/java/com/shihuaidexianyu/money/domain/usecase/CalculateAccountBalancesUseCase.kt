package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository

class CalculateAccountBalancesUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accounts: List<Account>,
        atTimeMillis: Long = Long.MAX_VALUE,
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()

        val accountIds = accounts.map(Account::id).toSet()
        val updatesByAccount = transactionRepository.queryAllBalanceUpdateRecords()
            .asSequence()
            .filter { it.accountId in accountIds && it.occurredAt <= atTimeMillis }
            .groupBy(BalanceUpdateRecord::accountId)
        val cashFlows = transactionRepository.queryAllActiveCashFlowRecords()
            .filter { it.accountId in accountIds && it.occurredAt <= atTimeMillis }
        val transfers = transactionRepository.queryAllActiveTransferRecords()
            .filter {
                (it.fromAccountId in accountIds || it.toAccountId in accountIds) &&
                    it.occurredAt <= atTimeMillis
            }
        val adjustments = transactionRepository.queryAllBalanceAdjustmentRecords()
            .filter {
                it.accountId in accountIds &&
                    it.occurredAt <= atTimeMillis
            }

        return accounts.associate { account ->
            account.id to LedgerBalanceCalculator.balanceAt(
                account = account,
                atTimeMillis = atTimeMillis,
                deltas = LedgerBalanceCalculator.deltasFromRecords(
                    account = account,
                    cashFlows = cashFlows,
                    transfers = transfers,
                    balanceUpdates = updatesByAccount[account.id].orEmpty(),
                    adjustments = adjustments,
                    atTimeMillis = atTimeMillis,
                ),
            )
        }
    }
}
