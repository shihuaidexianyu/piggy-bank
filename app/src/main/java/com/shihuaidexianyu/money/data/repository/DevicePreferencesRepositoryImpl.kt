package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.normalizeRecentAccountIds
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DevicePreferencesRepositoryImpl(
    private val context: Context,
) : DevicePreferencesRepository {
    override fun observe(): Flow<DevicePreferences> =
        context.appSettingsDataStore.data.map(DevicePreferencesMapper::fromPreferences)

    override suspend fun query(): DevicePreferences = observe().first()

    override suspend fun replace(preferences: DevicePreferences) {
        context.appSettingsDataStore.edit { DevicePreferencesMapper.write(it, preferences) }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) = edit { copy(themeMode = mode) }
    override suspend fun updateBiometricLock(enabled: Boolean) = edit { copy(biometricLock = enabled) }
    override suspend fun updateRelockDelay(delay: AppRelockDelay) = edit { copy(relockDelay = delay) }
    override suspend fun updateMaskAmountsInApp(enabled: Boolean) = edit { copy(maskAmountsInApp = enabled) }
    override suspend fun updateHideWidgetAmounts(enabled: Boolean) = edit { copy(hideWidgetAmounts = enabled) }
    override suspend fun updateHideNotificationAmounts(enabled: Boolean) = edit { copy(hideNotificationAmounts = enabled) }
    override suspend fun updateHideRecentTasks(enabled: Boolean) = edit { copy(hideRecentTasks = enabled) }
    override suspend fun updateNotificationPermissionRequested(requested: Boolean) =
        edit { copy(notificationPermissionRequested = requested) }

    override suspend fun updateHistoryFilters(filters: HistoryFilters) = edit { copy(historyFilters = filters) }
    override suspend fun updateRecentAccountIds(accountIds: List<Long>) =
        edit { copy(recentAccountIds = normalizeRecentAccountIds(accountIds)) }

    private suspend inline fun edit(crossinline transform: DevicePreferences.() -> DevicePreferences) {
        context.appSettingsDataStore.edit { mutable ->
            val current = DevicePreferencesMapper.fromPreferences(mutable)
            DevicePreferencesMapper.write(mutable, current.transform())
        }
    }
}

object DevicePreferencesMapper {
    fun fromPreferences(preferences: Preferences): DevicePreferences {
        val history = HistoryFilters(
            keyword = preferences[Keys.HistoryKeyword] ?: "",
            excludeKeyword = preferences[Keys.HistoryExcludeKeyword] ?: "",
            accountId = preferences[Keys.HistoryAccountId]?.takeIf { it >= 0L },
            dateStartAt = preferences[Keys.HistoryDateStartAt]?.takeIf { it >= 0L },
            dateEndAt = preferences[Keys.HistoryDateEndAt]?.takeIf { it >= 0L },
            minAmountText = preferences[Keys.HistoryMinAmountText] ?: "",
            maxAmountText = preferences[Keys.HistoryMaxAmountText] ?: "",
            amountDirection = preferences[Keys.HistoryAmountDirection] ?: "",
        )
        return DevicePreferences(
            themeMode = ThemeMode.fromValue(preferences[Keys.ThemeMode]),
            biometricLock = preferences[Keys.BiometricLock] ?: false,
            relockDelay = AppRelockDelay.fromValue(preferences[Keys.RelockDelay]),
            maskAmountsInApp = preferences[Keys.MaskAmountsInApp] ?: false,
            hideWidgetAmounts = preferences[Keys.HideWidgetAmounts] ?: false,
            hideNotificationAmounts = preferences[Keys.HideNotificationAmounts] ?: false,
            hideRecentTasks = preferences[Keys.HideRecentTasks] ?: false,
            notificationPermissionRequested = preferences[Keys.NotificationPermissionRequested] ?: false,
            historyFilters = history,
            recentAccountIds = normalizeRecentAccountIds(
                preferences[Keys.RecentAccountIds]
                    ?.split(',')
                    .orEmpty()
                    .mapNotNull(String::toLongOrNull),
            ),
        )
    }

    fun write(preferences: MutablePreferences, value: DevicePreferences) {
        val normalizedRecent = normalizeRecentAccountIds(value.recentAccountIds)
        preferences[Keys.ThemeMode] = value.themeMode.value
        preferences[Keys.BiometricLock] = value.biometricLock
        preferences[Keys.RelockDelay] = value.relockDelay.value
        preferences[Keys.MaskAmountsInApp] = value.maskAmountsInApp
        preferences[Keys.HideWidgetAmounts] = value.hideWidgetAmounts
        preferences[Keys.HideNotificationAmounts] = value.hideNotificationAmounts
        preferences[Keys.HideRecentTasks] = value.hideRecentTasks
        preferences[Keys.NotificationPermissionRequested] = value.notificationPermissionRequested
        writeNullableLong(preferences, Keys.HistoryAccountId, value.historyFilters.accountId)
        writeNullableLong(preferences, Keys.HistoryDateStartAt, value.historyFilters.dateStartAt)
        writeNullableLong(preferences, Keys.HistoryDateEndAt, value.historyFilters.dateEndAt)
        preferences[Keys.HistoryKeyword] = value.historyFilters.keyword
        preferences[Keys.HistoryExcludeKeyword] = value.historyFilters.excludeKeyword
        preferences[Keys.HistoryMinAmountText] = value.historyFilters.minAmountText
        preferences[Keys.HistoryMaxAmountText] = value.historyFilters.maxAmountText
        preferences[Keys.HistoryAmountDirection] = value.historyFilters.amountDirection
        preferences[Keys.RecentAccountIds] = normalizedRecent.joinToString(",")
    }

    private fun writeNullableLong(preferences: MutablePreferences, key: Preferences.Key<Long>, value: Long?) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }

    object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val BiometricLock = booleanPreferencesKey("biometric_lock")
        val RelockDelay = stringPreferencesKey("relock_delay")
        val MaskAmountsInApp = booleanPreferencesKey("mask_amounts_in_app")
        val HideWidgetAmounts = booleanPreferencesKey("hide_widget_amounts")
        val HideNotificationAmounts = booleanPreferencesKey("hide_notification_amounts")
        val HideRecentTasks = booleanPreferencesKey("hide_recent_tasks")
        val NotificationPermissionRequested = booleanPreferencesKey("notification_permission_requested")
        val HistoryKeyword = stringPreferencesKey("last_history_keyword")
        val HistoryExcludeKeyword = stringPreferencesKey("last_history_exclude_keyword")
        val HistoryAccountId = longPreferencesKey("last_history_account_id")
        val HistoryDateStartAt = longPreferencesKey("last_history_date_start_at")
        val HistoryDateEndAt = longPreferencesKey("last_history_date_end_at")
        val HistoryMinAmountText = stringPreferencesKey("last_history_min_amount_text")
        val HistoryMaxAmountText = stringPreferencesKey("last_history_max_amount_text")
        val HistoryAmountDirection = stringPreferencesKey("last_history_amount_direction")
        val RecentAccountIds = stringPreferencesKey("recent_account_ids")
    }
}
