package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class CalculateCurrentBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val ledgerAggregateRepository: LedgerAggregateRepository,
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
        val aggregate = ledgerAggregateRepository.queryAt(listOf(account), atTimeMillis).getValue(accountId)
        return LedgerBalanceCalculator.balanceAt(account, atTimeMillis, aggregate)
    }

    suspend fun before(accountId: Long, endExclusive: Long): Long {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        if (LedgerBalanceCalculator.openingAt(account) >= endExclusive) return 0L
        val aggregate = ledgerAggregateRepository.queryBefore(listOf(account), endExclusive).getValue(accountId)
        return LedgerBalanceCalculator.balanceBefore(account, endExclusive, aggregate)
    }
}
