package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CalculateAccountBalancesUseCase(
    private val ledgerAggregateRepository: LedgerAggregateRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        accounts: List<Account>,
        atTimeMillis: Long = clockProvider.nowMillis(),
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()
        if (atTimeMillis == Long.MAX_VALUE) return at(accounts, atTimeMillis)
        return before(accounts, LedgerBalanceCalculator.endExclusiveAfter(atTimeMillis))
    }

    private suspend fun at(
        accounts: List<Account>,
        atTimeMillis: Long,
    ): Map<Long, Long> {
        val aggregates = ledgerAggregateRepository.queryAt(accounts, atTimeMillis)
        return accounts.associate { account ->
            account.id to LedgerBalanceCalculator.balanceAt(
                account = account,
                atTimeMillis = atTimeMillis,
                aggregate = aggregates.getValue(account.id),
            )
        }
    }

    suspend fun before(
        accounts: List<Account>,
        endExclusive: Long,
    ): Map<Long, Long> {
        if (accounts.isEmpty()) return emptyMap()
        val aggregates = ledgerAggregateRepository.queryBefore(accounts, endExclusive)
        return accounts.associate { account ->
            account.id to LedgerBalanceCalculator.balanceBefore(
                account = account,
                endExclusive = endExclusive,
                aggregate = aggregates.getValue(account.id),
            )
        }
    }
}
