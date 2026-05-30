package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveHomeDashboardUseCaseTest {
    @Test
    fun `home dashboard counts balance update delta into inflow and outflow`() = runBlocking {
        val now = System.currentTimeMillis()
        val range = TimeRangeUtils.currentWeekRange(nowMillis = now)
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
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = range.startAtMillis + 1_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 500,
                purpose = "午饭",
                occurredAt = range.startAtMillis + 2_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_800,
                systemBalanceBeforeUpdate = 11_500,
                delta = 300,
                occurredAt = range.startAtMillis + 3_000,
                createdAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_100,
                systemBalanceBeforeUpdate = 11_800,
                delta = -700,
                occurredAt = range.startAtMillis + 4_000,
                createdAt = now,
            ),
        )

        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accountRepository,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            settingsRepository = FakeSettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
        )

        val snapshot = useCase().first()

        assertEquals(2_300, snapshot.periodNetInflow)
        assertEquals(1_200, snapshot.periodNetOutflow)
    }

    @Test
    fun `home dashboard exposes stale active accounts only`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val recurringReminderRepository = InMemoryRecurringReminderRepository(
            tickerFlow = MutableStateFlow(System.currentTimeMillis()).asStateFlow(),
        )
        val oldTime = 1_000L
        val staleBankId = accountRepository.createAccount(
            Account(
                name = "银行卡",
                initialBalance = 10_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        val stalePaymentId = accountRepository.createAccount(
            Account(
                name = "零钱",
                initialBalance = 2_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        val freshId = accountRepository.createAccount(
            Account(
                name = "新账户",
                initialBalance = 3_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = System.currentTimeMillis(),
            ),
        )
        val archivedId = accountRepository.createAccount(
            Account(
                name = "归档账户",
                initialBalance = 4_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        accountRepository.archiveAccount(archivedId, System.currentTimeMillis())
        reminderSettingsRepository.updateReminderConfig(
            freshId,
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.SUNDAY,
                hour = 23,
                minute = 59,
            ),
        )

        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            recurringReminderRepository = recurringReminderRepository,
            settingsRepository = FakeSettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
        )

        val snapshot = useCase().first()

        assertEquals(2, snapshot.staleAccountCount)
        assertEquals(setOf(staleBankId, stalePaymentId), snapshot.staleAccounts.map { it.id }.toSet())
        assertEquals(10_000, snapshot.accountBalances[staleBankId])
        assertEquals(2_000, snapshot.accountBalances[stalePaymentId])
    }

    private class FakeSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)

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
