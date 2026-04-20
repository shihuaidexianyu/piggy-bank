package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> {
        return context.appSettingsDataStore.data.map(::preferencesToSettings)
    }

    override suspend fun updateHomePeriod(period: HomePeriod) {
        context.appSettingsDataStore.edit { it[Keys.HomePeriod] = period.value }
    }

    override suspend fun updateCurrencySymbol(symbol: String) {
        context.appSettingsDataStore.edit { it[Keys.CurrencySymbol] = symbol.trim().ifEmpty { "¥" } }
    }

    override suspend fun updateShowStaleMark(show: Boolean) {
        context.appSettingsDataStore.edit { it[Keys.ShowStaleMark] = show }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        context.appSettingsDataStore.edit { it[Keys.ThemeMode] = themeMode.value }
    }

    override suspend fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        context.appSettingsDataStore.edit { it[Keys.AmountColorMode] = amountColorMode.value }
    }

    override suspend fun updateAccountGroupOrder(order: List<AccountGroupType>) {
        context.appSettingsDataStore.edit {
            it[Keys.AccountGroupOrder] = AccountGroupType.toStoredOrder(order)
        }
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
        context.appSettingsDataStore.edit {
            it[Keys.LastHistoryKeyword] = keyword
            it[Keys.LastHistoryAccountId] = accountId
            it[Keys.LastHistoryDateStartAt] = dateStartAt
            it[Keys.LastHistoryDateEndAt] = dateEndAt
            it[Keys.LastHistoryMinAmountText] = minAmountText
            it[Keys.LastHistoryMaxAmountText] = maxAmountText
            it[Keys.LastHistoryAmountDirection] = amountDirection
        }
    }

    private fun preferencesToSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            homePeriod = HomePeriod.fromValue(preferences[Keys.HomePeriod]),
            currencySymbol = preferences[Keys.CurrencySymbol] ?: "¥",
            showStaleMark = preferences[Keys.ShowStaleMark] ?: true,
            themeMode = ThemeMode.fromValue(preferences[Keys.ThemeMode]),
            amountColorMode = AmountColorMode.fromValue(preferences[Keys.AmountColorMode]),
            accountGroupOrder = AccountGroupType.fromStoredOrder(preferences[Keys.AccountGroupOrder]),
            lastHistoryKeyword = preferences[Keys.LastHistoryKeyword] ?: "",
            lastHistoryAccountId = preferences[Keys.LastHistoryAccountId] ?: -1L,
            lastHistoryDateStartAt = preferences[Keys.LastHistoryDateStartAt] ?: -1L,
            lastHistoryDateEndAt = preferences[Keys.LastHistoryDateEndAt] ?: -1L,
            lastHistoryMinAmountText = preferences[Keys.LastHistoryMinAmountText] ?: "",
            lastHistoryMaxAmountText = preferences[Keys.LastHistoryMaxAmountText] ?: "",
            lastHistoryAmountDirection = preferences[Keys.LastHistoryAmountDirection] ?: "",
        )
    }

    private object Keys {
        val HomePeriod = stringPreferencesKey("home_period")
        val CurrencySymbol = stringPreferencesKey("currency_symbol")
        val ShowStaleMark = booleanPreferencesKey("show_stale_mark")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AmountColorMode = stringPreferencesKey("amount_color_mode")
        val AccountGroupOrder = stringPreferencesKey("account_group_order")
        val LastHistoryKeyword = stringPreferencesKey("last_history_keyword")
        val LastHistoryAccountId = longPreferencesKey("last_history_account_id")
        val LastHistoryDateStartAt = longPreferencesKey("last_history_date_start_at")
        val LastHistoryDateEndAt = longPreferencesKey("last_history_date_end_at")
        val LastHistoryMinAmountText = stringPreferencesKey("last_history_min_amount_text")
        val LastHistoryMaxAmountText = stringPreferencesKey("last_history_max_amount_text")
        val LastHistoryAmountDirection = stringPreferencesKey("last_history_amount_direction")
    }
}
