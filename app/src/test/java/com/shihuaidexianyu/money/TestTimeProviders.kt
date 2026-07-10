package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountActivityMaxima
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import java.time.ZoneId

internal fun testClockProvider(nowMillis: Long = 4_102_444_800_000L): ClockProvider =
    ClockProvider { nowMillis }

internal fun testZoneIdProvider(zoneId: ZoneId = ZoneId.systemDefault()): ZoneIdProvider =
    ZoneIdProvider { zoneId }

@Suppress("FunctionName")
internal fun CalculateCurrentBalanceUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    clockProvider: ClockProvider,
): com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase =
    com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase(
        accountRepository = accountRepository,
        ledgerAggregateRepository = transactionRepository.requireLedgerAggregateRepository(),
        clockProvider = clockProvider,
    )

@Suppress("FunctionName")
internal fun CalculateCurrentBalanceUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
): com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase =
    CalculateCurrentBalanceUseCase(accountRepository, transactionRepository, testClockProvider())

@Suppress("FunctionName")
internal fun CalculateAccountBalancesUseCase(
    transactionRepository: TransactionRepository,
    clockProvider: ClockProvider,
): com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase =
    com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase(
        ledgerAggregateRepository = transactionRepository.requireLedgerAggregateRepository(),
        clockProvider = clockProvider,
    )

@Suppress("FunctionName")
internal fun CalculateAccountBalancesUseCase(
    transactionRepository: TransactionRepository,
): com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase =
    CalculateAccountBalancesUseCase(transactionRepository, testClockProvider())

@Suppress("FunctionName")
internal fun ResolveBalanceUpdateContextUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
): com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase =
    com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase(
        accountRepository = accountRepository,
        ledgerAggregateRepository = transactionRepository.requireLedgerAggregateRepository(),
    )

@Suppress("FunctionName")
internal fun RefreshAccountActivityStateUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
): com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase =
    com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase(
        accountRepository = accountRepository,
        ledgerAggregateRepository = transactionRepository.requireLedgerAggregateRepository(),
    )

private fun TransactionRepository.requireLedgerAggregateRepository(): LedgerAggregateRepository {
    return this as? LedgerAggregateRepository
        ?: error("Test transaction repository must also implement LedgerAggregateRepository")
}

internal class CountingLedgerAggregateRepository(
    private val delegate: LedgerAggregateRepository,
) : LedgerAggregateRepository {
    var beforeCalls: Int = 0
        private set
    var atCalls: Int = 0
        private set
    var activityCalls: Int = 0
        private set

    override suspend fun queryBefore(
        accounts: List<Account>,
        endExclusive: Long,
        excludingBalanceUpdateId: Long?,
    ): Map<Long, AccountLedgerAggregate> {
        beforeCalls += 1
        return delegate.queryBefore(accounts, endExclusive, excludingBalanceUpdateId)
    }

    override suspend fun queryAt(
        accounts: List<Account>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long?,
    ): Map<Long, AccountLedgerAggregate> {
        atCalls += 1
        return delegate.queryAt(accounts, atTimeMillis, excludingBalanceUpdateId)
    }

    override suspend fun queryActivityMaxima(accountId: Long): AccountActivityMaxima {
        activityCalls += 1
        return delegate.queryActivityMaxima(accountId)
    }
}
