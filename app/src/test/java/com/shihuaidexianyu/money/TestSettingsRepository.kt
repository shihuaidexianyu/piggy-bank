package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestSettingsRepository(
    initial: AppSettings = AppSettings(),
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
        excludeKeyword: String,
        accountId: Long,
        dateStartAt: Long,
        dateEndAt: Long,
        minAmountText: String,
        maxAmountText: String,
        amountDirection: String,
    ) {
        state.value = state.value.copy(
            lastHistoryKeyword = keyword,
            lastHistoryExcludeKeyword = excludeKeyword,
            lastHistoryAccountId = accountId,
            lastHistoryDateStartAt = dateStartAt,
            lastHistoryDateEndAt = dateEndAt,
            lastHistoryMinAmountText = minAmountText,
            lastHistoryMaxAmountText = maxAmountText,
            lastHistoryAmountDirection = amountDirection,
        )
    }
}
