package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.model.requireValidMonthlyBudget
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryPortableSettingsRepository(
    initial: PortableSettings = PortableSettings(),
) : PortableSettingsRepository {
    private val state = MutableStateFlow(initial.normalized())

    override fun observe(): Flow<PortableSettings> = state.asStateFlow()
    override suspend fun query(): PortableSettings = state.value

    override suspend fun updateCurrencySymbol(symbol: String) {
        state.value = state.value.copy(currencySymbol = normalizeCurrencySymbol(symbol))
    }

    override suspend fun updateAmountColorMode(mode: AmountColorMode) {
        state.value = state.value.copy(amountColorMode = mode)
    }

    override suspend fun updateMonthlyBudgetAmount(amount: Long?) {
        requireValidMonthlyBudget(amount)
        state.value = state.value.copy(monthlyBudgetAmount = amount)
    }

    override suspend fun replace(settings: PortableSettings) {
        state.value = settings.normalized()
    }

    private fun PortableSettings.normalized(): PortableSettings {
        requireValidMonthlyBudget(monthlyBudgetAmount)
        return copy(currencySymbol = normalizeCurrencySymbol(currencySymbol))
    }
}
