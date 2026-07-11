package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.StatsDashboardSnapshot
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StatsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    val hasSourceAccounts: Boolean = false,
    val errorMessage: String? = null,
    val retryToken: String? = null,
    val settings: PortableSettings = PortableSettings(),
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

internal fun StatsUiState.toAsyncContent(): AsyncContent<StatsUiState> {
    errorMessage?.let { return AsyncContent.Error(it, retryToken) }
    if (!hasCommittedContent) return AsyncContent.Loading
    if (isRefreshing) return AsyncContent.Refreshing(this)
    if (!hasSourceAccounts) return AsyncContent.Empty(EmptyKind.COMPLETELY_EMPTY)
    return AsyncContent.Data(this)
}

class StatsViewModel(
    private val observeStatsDashboardUseCase: ObserveStatsDashboardUseCase,
    private val devicePreferencesRepository: DevicePreferencesRepository,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(
        StatsRangeSelection(
            period = StatsPeriod.MONTH,
            anchorMillis = System.currentTimeMillis(),
        ),
    )
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var retryGeneration = 0

    init {
        observeStats()
    }

    fun retry() {
        observeStats()
    }

    private fun observeStats() {
        observationJob?.cancel()
        val hasCommittedContent = _uiState.value.hasCommittedContent
        _uiState.value = _uiState.value.copy(
            isLoading = !hasCommittedContent,
            isRefreshing = hasCommittedContent,
            errorMessage = null,
            retryToken = null,
        )
        observationJob = viewModelScope.launch {
            try {
                combine(
                    observeStatsDashboardUseCase(selectedRange),
                    devicePreferencesRepository.observe(),
                ) { snapshot, devicePreferences -> snapshot to devicePreferences }
                    .collect { (snapshot, devicePreferences) ->
                    val visibility = AmountPrivacy.from(devicePreferences)
                        .visibilityFor(AmountSurface.IN_APP)
                    _uiState.value = snapshot.toUiState(visibility)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("StatsViewModel", "Failed to observe stats", e) }
                retryGeneration += 1
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "分析加载失败，请重试",
                    retryToken = "stats:$retryGeneration",
                )
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

    private fun StatsDashboardSnapshot.toUiState(visibility: AmountVisibility): StatsUiState {
        return StatsUiState(
            isLoading = false,
            hasCommittedContent = true,
            hasSourceAccounts = accountBalances.isNotEmpty(),
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
            openingAssetsText = AmountFormatter.format(openingAssets, settings, visibility),
            closingAssetsText = AmountFormatter.format(closingAssets, settings, visibility),
            totalInflowText = AmountFormatter.format(totalInflow, settings, visibility),
            totalOutflowText = AmountFormatter.format(totalOutflow, settings, visibility),
            netCashFlowText = formatSignedAmount(netCashFlow, settings, visibility),
            assetChangeText = formatSignedAmount(assetChange, settings, visibility),
            assetAdjustmentText = formatSignedAmount(assetAdjustment, settings, visibility),
            manualAdjustmentText = formatSignedAmount(manualAdjustmentNet, settings, visibility),
            reconciliationText = formatSignedAmount(reconciliationNet, settings, visibility),
            openingAssetsFlowText = formatFlowAmount(openingAssets, visibility),
            closingAssetsFlowText = formatFlowAmount(closingAssets, visibility),
            totalInflowFlowText = formatFlowAmount(totalInflow, visibility),
            totalOutflowFlowText = formatFlowAmount(totalOutflow, visibility),
            netCashFlowFlowText = formatFlowAmount(netCashFlow, visibility),
            assetAdjustmentFlowText = formatFlowAmount(assetAdjustment, visibility),
            manualAdjustmentFlowText = formatFlowAmount(manualAdjustmentNet, visibility),
            reconciliationFlowText = formatFlowAmount(reconciliationNet, visibility),
        )
    }
}

private fun isCurrentRange(snapshot: StatsDashboardSnapshot): Boolean {
    val now = Instant.now()
    val start = Instant.ofEpochMilli(snapshot.range.startInclusive)
    val end = Instant.ofEpochMilli(snapshot.range.endExclusive)
    return !now.isBefore(start) && now.isBefore(end)
}

private fun formatSignedAmount(
    amount: Long,
    settings: PortableSettings,
    visibility: AmountVisibility,
): String {
    if (visibility == AmountVisibility.MASKED) return AmountFormatter.format(amount, settings, visibility)
    return when {
        amount > 0L -> "+${AmountFormatter.format(amount, settings, visibility)}"
        else -> AmountFormatter.format(amount, settings, visibility)
    }
}

private fun formatFlowAmount(amount: Long, visibility: AmountVisibility): String {
    if (visibility == AmountVisibility.MASKED) return "••••"
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
    val start = java.time.Instant.ofEpochMilli(snapshot.range.startInclusive).atZone(zoneId).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(snapshot.range.endExclusive).atZone(zoneId).toLocalDate().minusDays(1)
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
