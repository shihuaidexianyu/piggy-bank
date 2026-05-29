package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.StatsDashboardSnapshot
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val selectedPeriod: StatsPeriod = StatsPeriod.MONTH,
    val rangeText: String = "",
    val openingAssets: Long = 0L,
    val closingAssets: Long = 0L,
    val totalInflow: Long = 0L,
    val totalOutflow: Long = 0L,
    val netCashFlow: Long = 0L,
    val assetChange: Long = 0L,
    val assetAdjustment: Long = 0L,
    val openingAssetsText: String = "",
    val closingAssetsText: String = "",
    val totalInflowText: String = "",
    val totalOutflowText: String = "",
    val netCashFlowText: String = "",
    val assetChangeText: String = "",
    val assetAdjustmentText: String = "",
)

class StatsViewModel(
    private val observeStatsDashboardUseCase: ObserveStatsDashboardUseCase,
) : ViewModel() {
    private val selectedPeriod = MutableStateFlow(StatsPeriod.MONTH)
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                observeStatsDashboardUseCase(selectedPeriod).collect { snapshot ->
                    _uiState.value = snapshot.toUiState()
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsViewModel", "Failed to observe stats", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updatePeriod(period: StatsPeriod) {
        selectedPeriod.value = period
    }

    private fun StatsDashboardSnapshot.toUiState(): StatsUiState {
        return StatsUiState(
            isLoading = false,
            settings = settings,
            selectedPeriod = period,
            rangeText = formatRangeText(this),
            openingAssets = openingAssets,
            closingAssets = closingAssets,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            assetChange = assetChange,
            assetAdjustment = assetAdjustment,
            openingAssetsText = AmountFormatter.format(openingAssets, settings),
            closingAssetsText = AmountFormatter.format(closingAssets, settings),
            totalInflowText = AmountFormatter.format(totalInflow, settings),
            totalOutflowText = AmountFormatter.format(totalOutflow, settings),
            netCashFlowText = formatSignedAmount(netCashFlow, settings),
            assetChangeText = formatSignedAmount(assetChange, settings),
            assetAdjustmentText = formatSignedAmount(assetAdjustment, settings),
        )
    }
}

private fun formatSignedAmount(amount: Long, settings: AppSettings): String {
    return when {
        amount > 0L -> "+${AmountFormatter.format(amount, settings)}"
        else -> AmountFormatter.format(amount, settings)
    }
}

private fun formatRangeText(snapshot: StatsDashboardSnapshot): String {
    val zoneId = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(snapshot.range.startAtMillis).atZone(zoneId).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(snapshot.range.endAtMillis).atZone(zoneId).toLocalDate()
    return when (snapshot.period) {
        StatsPeriod.YEAR -> "${start.year}年"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("M月d日")
            "${start.format(formatter)} - ${end.format(formatter)}"
        }
    }
}
