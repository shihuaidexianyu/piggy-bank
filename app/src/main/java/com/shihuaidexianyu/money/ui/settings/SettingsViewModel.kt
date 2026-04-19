package com.shihuaidexianyu.money.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.observeSettings()
            .map { SettingsUiState(settings = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    fun updateHomePeriod(period: HomePeriod) {
        viewModelScope.launch { settingsRepository.updateHomePeriod(period) }
    }

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch { settingsRepository.updateCurrencySymbol(symbol) }
    }

    fun updateShowStaleMark(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowStaleMark(show) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(themeMode) }
    }
}
