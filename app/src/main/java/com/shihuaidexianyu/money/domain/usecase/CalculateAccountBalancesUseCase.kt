package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CalculateAccountBalancesUseCase(
    private val transactionRepository: TransactionRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accounts: List<Account>,
        atTimeMillis: Long = clockProvider.nowMillis(),
    ): Map<Long, Long> {
        if (atTimeMillis == Long.MAX_VALUE) return at(accounts, atTimeMillis)
        return before(accounts, LedgerBalanceCalculator.endExclusiveAfter(atTimeMillis))
    }

    private suspend fun at(
        accounts: List<Account>,
        atTimeMillis: Long,
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()

        val accountIds = accounts.map(Account::id).toSet()
        val updatesByAccount = transactionRepository.queryAllBalanceUpdateRecords()
            .asSequence()
            .filter { it.accountId in accountIds }
            .groupBy(BalanceUpdateRecord::accountId)
        val cashFlows = transactionRepository.queryAllActiveCashFlowRecords()
            .filter { it.accountId in accountIds }
        val transfers = transactionRepository.queryAllActiveTransferRecords()
            .filter { it.fromAccountId in accountIds || it.toAccountId in accountIds }
        val adjustments = transactionRepository.queryAllBalanceAdjustmentRecords()
            .filter { it.accountId in accountIds }

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

    suspend fun before(
        accounts: List<Account>,
        endExclusive: Long,
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()

        val accountIds = accounts.map(Account::id).toSet()
        val updatesByAccount = transactionRepository.queryAllBalanceUpdateRecords()
            .asSequence()
            .filter { it.accountId in accountIds && it.occurredAt < endExclusive }
            .groupBy(BalanceUpdateRecord::accountId)
        val cashFlows = transactionRepository.queryAllActiveCashFlowRecords()
            .filter { it.accountId in accountIds && it.occurredAt < endExclusive }
        val transfers = transactionRepository.queryAllActiveTransferRecords()
            .filter {
                (it.fromAccountId in accountIds || it.toAccountId in accountIds) &&
                    it.occurredAt < endExclusive
            }
        val adjustments = transactionRepository.queryAllBalanceAdjustmentRecords()
            .filter {
                it.accountId in accountIds &&
                    it.occurredAt < endExclusive
            }

        return accounts.associate { account ->
            account.id to LedgerBalanceCalculator.balanceBefore(
                account = account,
                endExclusive = endExclusive,
                deltas = LedgerBalanceCalculator.deltasFromRecordsBefore(
                    account = account,
                    cashFlows = cashFlows,
                    transfers = transfers,
                    balanceUpdates = updatesByAccount[account.id].orEmpty(),
                    adjustments = adjustments,
                    endExclusive = endExclusive,
                ),
            )
        }
    }
}
