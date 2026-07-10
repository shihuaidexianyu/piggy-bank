package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountActivityMaxima
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate

interface LedgerAggregateRepository {
    suspend fun queryBefore(
        accounts: List<Account>,
        endExclusive: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Map<Long, AccountLedgerAggregate>

    suspend fun queryAt(
        accounts: List<Account>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Map<Long, AccountLedgerAggregate>

    suspend fun queryActivityMaxima(accountId: Long): AccountActivityMaxima
}
