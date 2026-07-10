package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository

class RefreshAccountActivityStateUseCase(
    private val accountRepository: AccountRepository,
    private val ledgerAggregateRepository: LedgerAggregateRepository,
) {
    suspend operator fun invoke(accountId: Long) {
        val account = accountRepository.getAccountById(accountId) ?: return
        val maxima = ledgerAggregateRepository.queryActivityMaxima(accountId)
        val lastUsedAt = maxOf(account.createdAt, maxima.maxActiveOccurredAt ?: account.createdAt)

        accountRepository.updateLastUsedAt(accountId, lastUsedAt)
        accountRepository.updateLastBalanceUpdateAt(accountId, maxima.maxBalanceUpdateOccurredAt)
    }
}
