package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveStatsDashboardUseCaseTest {
    @Test
    fun `stats dashboard exposes asset flow reconciliation`() = runBlocking {
        val range = TimeRangeUtils.currentMonthRange()
        val now = range.startAtMillis + 1_000
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = range.startAtMillis - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 3_000,
                purpose = "工资",
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 800,
                purpose = "餐饮",
                occurredAt = now + 1_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -200,
                sourceUpdateRecordId = 0L,
                occurredAt = now + 2_000,
                createdAt = now,
            ),
        )

        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accountRepository,
            settingsRepository = FakeSettingsRepository(),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
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
        assertEquals(3_000, snapshot.totalInflow)
        assertEquals(800, snapshot.totalOutflow)
        assertEquals(2_200, snapshot.netCashFlow)
        assertEquals(2_000, snapshot.assetChange)
        assertEquals(-200, snapshot.assetAdjustment)
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
                createdAt = januaryRange.startAtMillis - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 3_000,
                purpose = "一月工资",
                occurredAt = januaryRange.startAtMillis + 1_000,
                createdAt = januaryRange.startAtMillis + 1_000,
                updatedAt = januaryRange.startAtMillis + 1_000,
            ),
        )

        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accountRepository,
            settingsRepository = FakeSettingsRepository(),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
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

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())

        override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

        override suspend fun updateHomePeriod(period: HomePeriod) {
            state.value = state.value.copy(homePeriod = period)
        }

        override suspend fun updateCurrencySymbol(symbol: String) {
            state.value = state.value.copy(currencySymbol = symbol)
        }

        override suspend fun updateShowStaleMark(show: Boolean) {
            state.value = state.value.copy(showStaleMark = show)
        }

        override suspend fun updateThemeMode(themeMode: ThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }

        override suspend fun updateAmountColorMode(amountColorMode: AmountColorMode) {
            state.value = state.value.copy(amountColorMode = amountColorMode)
        }

        override suspend fun updateLastHistoryFilters(
            keyword: String,
            accountId: Long,
            dateStartAt: Long,
            dateEndAt: Long,
            minAmountText: String,
            maxAmountText: String,
            amountDirection: String,
        ) {
            state.value = state.value.copy(
                lastHistoryKeyword = keyword,
                lastHistoryAccountId = accountId,
                lastHistoryDateStartAt = dateStartAt,
                lastHistoryDateEndAt = dateEndAt,
                lastHistoryMinAmountText = minAmountText,
                lastHistoryMaxAmountText = maxAmountText,
                lastHistoryAmountDirection = amountDirection,
            )
        }
    }
}
