package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountOrderingUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateAccountOrderingUseCaseTest {
    @Test
    fun `rolls back account order when settings update fails`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val firstId = accountRepository.createAccount(
            AccountEntity(name = "A", groupType = "payment", initialBalance = 0, createdAt = 1, displayOrder = 0),
        )
        val secondId = accountRepository.createAccount(
            AccountEntity(name = "B", groupType = "bank", initialBalance = 0, createdAt = 1, displayOrder = 1),
        )
        val settingsRepository = FailingSettingsRepository()
        val useCase = UpdateAccountOrderingUseCase(
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(accountRepository),
        )

        val error = assertFailsWith<IllegalStateException> {
            useCase(
                groupOrder = listOf(AccountGroupType.BANK, AccountGroupType.PAYMENT),
                orderedAccountIds = listOf(secondId, firstId),
            )
        }

        assertEquals("settings write failed", error.message)
        assertEquals(0, accountRepository.getAccountById(firstId)?.displayOrder)
        assertEquals(1, accountRepository.getAccountById(secondId)?.displayOrder)
        assertEquals(AppSettings().accountGroupOrder, settingsRepository.observeSettingsSnapshot().accountGroupOrder)
    }

    private class FailingSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings(homePeriod = HomePeriod.WEEK))

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

        override suspend fun updateAccountGroupOrder(order: List<AccountGroupType>) {
            throw IllegalStateException("settings write failed")
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
            throw IllegalStateException("settings write failed")
        }

        fun observeSettingsSnapshot(): AppSettings = state.value
    }
}
