package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.normalizeRecentAccountIds
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryDevicePreferencesRepository(
    initial: DevicePreferences = DevicePreferences(),
) : DevicePreferencesRepository {
    private val state = MutableStateFlow(initial.normalized())

    override fun observe(): Flow<DevicePreferences> = state.asStateFlow()
    override suspend fun query(): DevicePreferences = state.value

    override suspend fun replace(preferences: DevicePreferences) {
        state.value = preferences.normalized()
    }

    override suspend fun updateThemeMode(mode: ThemeMode) = update { copy(themeMode = mode) }
    override suspend fun updateBiometricLock(enabled: Boolean) = update { copy(biometricLock = enabled) }
    override suspend fun updateRelockDelay(delay: AppRelockDelay) = update { copy(relockDelay = delay) }
    override suspend fun updateMaskAmountsInApp(enabled: Boolean) = update { copy(maskAmountsInApp = enabled) }
    override suspend fun updateHideWidgetAmounts(enabled: Boolean) = update { copy(hideWidgetAmounts = enabled) }
    override suspend fun updateHideNotificationAmounts(enabled: Boolean) = update { copy(hideNotificationAmounts = enabled) }
    override suspend fun updateHideRecentTasks(enabled: Boolean) = update { copy(hideRecentTasks = enabled) }
    override suspend fun updateNotificationPermissionRequested(requested: Boolean) =
        update { copy(notificationPermissionRequested = requested) }

    override suspend fun updateHistoryFilters(filters: HistoryFilters) = update { copy(historyFilters = filters) }

    override suspend fun updateRecentAccountIds(accountIds: List<Long>) =
        update { copy(recentAccountIds = normalizeRecentAccountIds(accountIds)) }

    private inline fun update(transform: DevicePreferences.() -> DevicePreferences) {
        state.value = state.value.transform().normalized()
    }

    private fun DevicePreferences.normalized(): DevicePreferences =
        copy(recentAccountIds = normalizeRecentAccountIds(recentAccountIds))
}
