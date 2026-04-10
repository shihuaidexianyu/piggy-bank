package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
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
            AccountEntity(
                name = "主账户",
                groupType = AccountGroupType.BANK.value,
                initialBalance = 10_000,
                createdAt = range.startAtMillis - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecordEntity(
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
            CashFlowRecordEntity(
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
            BalanceUpdateRecordEntity(
                accountId = accountId,
                actualBalance = 11_800,
                systemBalanceBeforeUpdate = 11_500,
                delta = 300,
                occurredAt = range.startAtMillis + 3_000,
                createdAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecordEntity(
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

        override suspend fun updateAccountGroupOrder(order: List<AccountGroupType>) {
            state.value = state.value.copy(accountGroupOrder = order)
        }
    }
}
