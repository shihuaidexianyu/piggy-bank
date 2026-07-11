package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface DevicePreferencesRepository {
    fun observe(): Flow<DevicePreferences>
    suspend fun query(): DevicePreferences
    suspend fun replace(preferences: DevicePreferences)
    suspend fun migrateExternalPrivacyDefaultsIfNeeded()
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateBiometricLock(enabled: Boolean)
    suspend fun enableBiometricLockWithPrivacyDefaults()
    suspend fun updateRelockDelay(delay: AppRelockDelay)
    suspend fun updateMaskAmountsInApp(enabled: Boolean)
    suspend fun updateHideWidgetAmounts(enabled: Boolean)
    suspend fun updateHideNotificationAmounts(enabled: Boolean)
    suspend fun updateHideRecentTasks(enabled: Boolean)
    suspend fun updateNotificationPermissionRequested(requested: Boolean)
    suspend fun updateHistoryFilters(filters: HistoryFilters)
    suspend fun updateRecentAccountIds(accountIds: List<Long>)
}
