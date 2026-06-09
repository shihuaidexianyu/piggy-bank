package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.StatsDashboardSnapshot
import com.shihuaidexianyu.money.util.AmountFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val selectedPeriod: StatsPeriod = StatsPeriod.MONTH,
    val isCurrentRange: Boolean = true,
    val rangeText: String = "",
    val openingAssets: Long = 0L,
    val closingAssets: Long = 0L,
    val totalInflow: Long = 0L,
    val totalOutflow: Long = 0L,
    val netCashFlow: Long = 0L,
    val assetChange: Long = 0L,
    val assetAdjustment: Long = 0L,
    val manualAdjustmentNet: Long = 0L,
    val reconciliationNet: Long = 0L,
    val openingAssetsText: String = "",
    val closingAssetsText: String = "",
    val totalInflowText: String = "",
    val totalOutflowText: String = "",
    val netCashFlowText: String = "",
    val assetChangeText: String = "",
    val assetAdjustmentText: String = "",
    val manualAdjustmentText: String = "",
    val reconciliationText: String = "",
    val openingAssetsFlowText: String = "",
    val closingAssetsFlowText: String = "",
    val totalInflowFlowText: String = "",
    val totalOutflowFlowText: String = "",
    val netCashFlowFlowText: String = "",
    val assetAdjustmentFlowText: String = "",
    val manualAdjustmentFlowText: String = "",
    val reconciliationFlowText: String = "",
)

class StatsViewModel(
    private val observeStatsDashboardUseCase: ObserveStatsDashboardUseCase,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(
        StatsRangeSelection(
            period = StatsPeriod.MONTH,
            anchorMillis = System.currentTimeMillis(),
        ),
    )
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                observeStatsDashboardUseCase(selectedRange).collect { snapshot ->
                    _uiState.value = snapshot.toUiState()
                }
            } catch (e: Exception) {
                android.util.Log.e("StatsViewModel", "Failed to observe stats", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updatePeriod(period: StatsPeriod) {
        selectedRange.value = StatsRangeSelection(
            period = period,
            anchorMillis = System.currentTimeMillis(),
        )
    }

    fun moveToPreviousRange() {
        moveRange(-1)
    }

    fun moveToNextRange() {
        moveRange(1)
    }

    fun resetToCurrentRange() {
        val current = selectedRange.value
        selectedRange.value = current.copy(anchorMillis = System.currentTimeMillis())
    }

    private fun moveRange(amount: Long) {
        val current = selectedRange.value
        val zoneId = ZoneId.systemDefault()
        val anchor = Instant.ofEpochMilli(current.anchorMillis).atZone(zoneId)
        val shifted = when (current.period) {
            StatsPeriod.WEEK -> anchor.plusWeeks(amount)
            StatsPeriod.MONTH -> anchor.plusMonths(amount)
            StatsPeriod.YEAR -> anchor.plusYears(amount)
        }
        selectedRange.value = current.copy(anchorMillis = shifted.toInstant().toEpochMilli())
    }

    private fun StatsDashboardSnapshot.toUiState(): StatsUiState {
        return StatsUiState(
            isLoading = false,
            settings = settings,
            selectedPeriod = period,
            isCurrentRange = isCurrentRange(this),
            rangeText = formatRangeText(this),
            openingAssets = openingAssets,
            closingAssets = closingAssets,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            assetChange = assetChange,
            assetAdjustment = assetAdjustment,
            manualAdjustmentNet = manualAdjustmentNet,
            reconciliationNet = reconciliationNet,
            openingAssetsText = AmountFormatter.format(openingAssets, settings),
            closingAssetsText = AmountFormatter.format(closingAssets, settings),
            totalInflowText = AmountFormatter.format(totalInflow, settings),
            totalOutflowText = AmountFormatter.format(totalOutflow, settings),
            netCashFlowText = formatSignedAmount(netCashFlow, settings),
            assetChangeText = formatSignedAmount(assetChange, settings),
            assetAdjustmentText = formatSignedAmount(assetAdjustment, settings),
            manualAdjustmentText = formatSignedAmount(manualAdjustmentNet, settings),
            reconciliationText = formatSignedAmount(reconciliationNet, settings),
            openingAssetsFlowText = formatFlowAmount(openingAssets),
            closingAssetsFlowText = formatFlowAmount(closingAssets),
            totalInflowFlowText = formatFlowAmount(totalInflow),
            totalOutflowFlowText = formatFlowAmount(totalOutflow),
            netCashFlowFlowText = formatFlowAmount(netCashFlow),
            assetAdjustmentFlowText = formatFlowAmount(assetAdjustment),
            manualAdjustmentFlowText = formatFlowAmount(manualAdjustmentNet),
            reconciliationFlowText = formatFlowAmount(reconciliationNet),
        )
    }
}

private fun isCurrentRange(snapshot: StatsDashboardSnapshot): Boolean {
    val now = Instant.now()
    val start = Instant.ofEpochMilli(snapshot.range.startAtMillis)
    val end = Instant.ofEpochMilli(snapshot.range.endAtMillis)
    return !now.isBefore(start) && !now.isAfter(end)
}

private fun formatSignedAmount(amount: Long, settings: AppSettings): String {
    return when {
        amount > 0L -> "+${AmountFormatter.format(amount, settings)}"
        else -> AmountFormatter.format(amount, settings)
    }
}

private fun formatFlowAmount(amount: Long): String {
    val sign = if (amount < 0L) "-" else ""
    val absolute = BigDecimal.valueOf(amount)
        .movePointLeft(2)
        .abs()
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
    return "$sign$absolute"
}

private fun formatRangeText(snapshot: StatsDashboardSnapshot): String {
    val zoneId = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(snapshot.range.startAtMillis).atZone(zoneId).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(snapshot.range.endAtMillis).atZone(zoneId).toLocalDate()
    val currentYear = java.time.LocalDate.now(zoneId).year
    return when (snapshot.period) {
        StatsPeriod.YEAR -> "${start.year}年"
        StatsPeriod.MONTH -> {
            if (start.year == currentYear) {
                DateTimeFormatter.ofPattern("M月").format(start)
            } else {
                DateTimeFormatter.ofPattern("yyyy年M月").format(start)
            }
        }
        else -> {
            val formatter = if (start.year == currentYear && end.year == currentYear) {
                DateTimeFormatter.ofPattern("M月d日")
            } else {
                DateTimeFormatter.ofPattern("yyyy/M/d")
            }
            "${start.format(formatter)} - ${end.format(formatter)}"
        }
    }
}
