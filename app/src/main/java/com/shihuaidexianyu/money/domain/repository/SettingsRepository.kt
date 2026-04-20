package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AccountGroupType
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
    suspend fun updateAccountGroupOrder(order: List<AccountGroupType>)
    suspend fun updateLastHistoryFilters(
        keyword: String,
        accountId: Long,
        dateStartAt: Long,
        dateEndAt: Long,
        minAmountText: String,
        maxAmountText: String,
        amountDirection: String,
    )
}
