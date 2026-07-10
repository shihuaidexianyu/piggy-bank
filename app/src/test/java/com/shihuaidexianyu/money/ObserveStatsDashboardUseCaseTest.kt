package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemorySettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveStatsDashboardUseCaseTest {
    @Test
    fun `stats dashboard exposes asset flow reconciliation`() = runBlocking {
        val range = TimeRangeUtils.currentMonthRange()
        val now = range.startInclusive + 1_000
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = range.startInclusive - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 3_000,
                note = "工资",
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 800,
                note = "餐饮",
                occurredAt = now + 1_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -200,
                occurredAt = now + 2_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 12_500,
                systemBalanceBeforeUpdate = 12_000,
                delta = 500,
                occurredAt = now + 3_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )

        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accountRepository,
            settingsRepository = InMemorySettingsRepository(),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase(
            MutableStateFlow(
                StatsRangeSelection(
                    period = StatsPeriod.MONTH,
                    anchorMillis = now,
                ),
            ),
        ).first()

        assertEquals(10_000, snapshot.openingAssets)
        assertEquals(12_500, snapshot.closingAssets)
        assertEquals(3_000, snapshot.totalInflow)
        assertEquals(800, snapshot.totalOutflow)
        assertEquals(2_200, snapshot.netCashFlow)
        assertEquals(2_500, snapshot.assetChange)
        assertEquals(300, snapshot.assetAdjustment)
        assertEquals(-200, snapshot.manualAdjustmentNet)
        assertEquals(500, snapshot.reconciliationNet)
        assertEquals(
            snapshot.closingAssets,
            snapshot.openingAssets + snapshot.netCashFlow + snapshot.assetAdjustment,
        )
    }

    @Test
    fun `stats dashboard can target a past month`() = runBlocking {
        val zoneId = java.time.ZoneId.systemDefault()
        val januaryAnchor = java.time.LocalDate.of(2024, 1, 15)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val januaryRange = TimeRangeUtils.statsRange(StatsPeriod.MONTH, zoneId, januaryAnchor)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = januaryRange.startInclusive - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 3_000,
                note = "一月工资",
                occurredAt = januaryRange.startInclusive + 1_000,
                createdAt = januaryRange.startInclusive + 1_000,
                updatedAt = januaryRange.startInclusive + 1_000,
                operationId = testOperationId(),
            ),
        )

        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accountRepository,
            settingsRepository = InMemorySettingsRepository(),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase(
            MutableStateFlow(
                StatsRangeSelection(
                    period = StatsPeriod.MONTH,
                    anchorMillis = januaryAnchor,
                ),
            ),
        ).first()

        assertEquals(januaryRange, snapshot.range)
        assertEquals(3_000, snapshot.totalInflow)
        assertEquals(13_000, snapshot.closingAssets)
    }

    @Test
    fun `stats dashboard treats in-range account initial balance as opening assets`() = runBlocking {
        val range = TimeRangeUtils.currentMonthRange()
        val now = range.startInclusive + 1_000
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "新账户",
                initialBalance = 10_000,
                createdAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 2_000,
                note = "工资",
                occurredAt = now + 1_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )

        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accountRepository,
            settingsRepository = InMemorySettingsRepository(),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase(
            MutableStateFlow(
                StatsRangeSelection(
                    period = StatsPeriod.MONTH,
                    anchorMillis = now,
                ),
            ),
        ).first()

        assertEquals(10_000, snapshot.openingAssets)
        assertEquals(12_000, snapshot.closingAssets)
        assertEquals(2_000, snapshot.totalInflow)
        assertEquals(2_000, snapshot.netCashFlow)
        assertEquals(2_000, snapshot.assetChange)
        assertEquals(0, snapshot.assetAdjustment)
        assertEquals(
            snapshot.closingAssets,
            snapshot.openingAssets + snapshot.netCashFlow + snapshot.assetAdjustment,
        )
    }
}
