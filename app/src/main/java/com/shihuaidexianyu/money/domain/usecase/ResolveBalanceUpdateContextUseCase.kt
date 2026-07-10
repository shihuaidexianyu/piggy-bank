package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository

data class BalanceUpdateContext(
    val systemBalanceBeforeUpdate: Long,
)

class ResolveBalanceUpdateContextUseCase(
    private val accountRepository: AccountRepository,
    private val ledgerAggregateRepository: LedgerAggregateRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        occurredAt: Long,
        excludingRecordId: Long? = null,
    ): BalanceUpdateContext {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        if (!LedgerBalanceCalculator.isOpenAt(account, occurredAt)) {
            return BalanceUpdateContext(systemBalanceBeforeUpdate = 0L)
        }

        val aggregate = if (occurredAt == Long.MAX_VALUE) {
            ledgerAggregateRepository.queryAt(
                accounts = listOf(account),
                atTimeMillis = occurredAt,
                excludingBalanceUpdateId = excludingRecordId,
            )
        } else {
            ledgerAggregateRepository.queryBefore(
                accounts = listOf(account),
                endExclusive = LedgerBalanceCalculator.endExclusiveAfter(occurredAt),
                excludingBalanceUpdateId = excludingRecordId,
            )
        }.getValue(accountId)

        return BalanceUpdateContext(
            systemBalanceBeforeUpdate = LedgerBalanceCalculator.balanceAt(
                account = account,
                atTimeMillis = occurredAt,
                aggregate = aggregate,
            ),
        )
    }
}
