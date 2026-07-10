package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
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
): com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase =
    com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        clockProvider = testClockProvider(),
    )

@Suppress("FunctionName")
internal fun CalculateAccountBalancesUseCase(
    transactionRepository: TransactionRepository,
): com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase =
    com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase(
        transactionRepository = transactionRepository,
        clockProvider = testClockProvider(),
    )
