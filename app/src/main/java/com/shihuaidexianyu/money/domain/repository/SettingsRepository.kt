package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateHomePeriod(period: HomePeriod)
    suspend fun updateCurrencySymbol(symbol: String)
    suspend fun updateShowStaleMark(show: Boolean)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateAmountColorMode(amountColorMode: AmountColorMode)
    suspend fun updateBiometricLock(enabled: Boolean)
    suspend fun updateDynamicColor(enabled: Boolean)
    suspend fun replaceSettings(settings: AppSettings) {
        updateHomePeriod(settings.homePeriod)
        updateCurrencySymbol(settings.currencySymbol)
        updateShowStaleMark(settings.showStaleMark)
        updateThemeMode(settings.themeMode)
        updateAmountColorMode(settings.amountColorMode)
        updateBiometricLock(settings.biometricLock)
        updateDynamicColor(settings.dynamicColor)
        updateLastHistoryFilters(
            keyword = settings.lastHistoryKeyword,
            excludeKeyword = settings.lastHistoryExcludeKeyword,
            accountId = settings.lastHistoryAccountId,
            dateStartAt = settings.lastHistoryDateStartAt,
            dateEndAt = settings.lastHistoryDateEndAt,
            minAmountText = settings.lastHistoryMinAmountText,
            maxAmountText = settings.lastHistoryMaxAmountText,
            amountDirection = settings.lastHistoryAmountDirection,
        )
    }

    suspend fun updateLastHistoryFilters(
        keyword: String,
        excludeKeyword: String,
        accountId: Long,
        dateStartAt: Long,
        dateEndAt: Long,
        minAmountText: String,
        maxAmountText: String,
        amountDirection: String,
    )
}
