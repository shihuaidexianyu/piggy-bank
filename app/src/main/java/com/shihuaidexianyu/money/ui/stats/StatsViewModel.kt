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

data class StatsPurposeUiModel(
    val purpose: String,
    val amount: Long,
    val amountText: String,
    val share: Float,
)

data class StatsTrendUiPoint(
    val label: String,
    val inflow: Long,
    val outflow: Long,
)

data class StatsAccountUiModel(
    val accountId: Long,
    val name: String,
    val colorName: String,
    val balance: Long,
    val balanceText: String,
    val share: Float,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val selectedPeriod: StatsPeriod = StatsPeriod.MONTH,
    val rangeText: String = "",
    val totalInflow: Long = 0L,
    val totalOutflow: Long = 0L,
    val netCashFlow: Long = 0L,
    val assetChange: Long = 0L,
    val totalInflowText: String = "",
    val totalOutflowText: String = "",
    val netCashFlowText: String = "",
    val assetChangeText: String = "",
    val purposeBreakdown: List<StatsPurposeUiModel> = emptyList(),
    val trendPoints: List<StatsTrendUiPoint> = emptyList(),
    val accountBalances: List<StatsAccountUiModel> = emptyList(),
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
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            assetChange = assetChange,
            totalInflowText = AmountFormatter.format(totalInflow, settings),
            totalOutflowText = AmountFormatter.format(totalOutflow, settings),
            netCashFlowText = formatSignedAmount(netCashFlow, settings),
            assetChangeText = formatSignedAmount(assetChange, settings),
            purposeBreakdown = purposeBreakdown.toPurposeUi(totalOutflow, settings),
            trendPoints = toTrendUiPoints(),
            accountBalances = accountBalances.toAccountUi(settings),
        )
    }

    private fun List<com.shihuaidexianyu.money.domain.usecase.StatsPurposeBreakdown>.toPurposeUi(
        totalOutflow: Long,
        settings: AppSettings,
    ): List<StatsPurposeUiModel> {
        return take(8).map { item ->
            StatsPurposeUiModel(
                purpose = item.purpose,
                amount = item.amount,
                amountText = AmountFormatter.format(item.amount, settings),
                share = if (totalOutflow > 0L) item.amount.toFloat() / totalOutflow else 0f,
            )
        }
    }

    private fun StatsDashboardSnapshot.toTrendUiPoints(): List<StatsTrendUiPoint> {
        return when (period) {
            StatsPeriod.YEAR -> dailyPoints
                .groupBy { it.date.monthValue }
                .map { (month, points) ->
                    StatsTrendUiPoint(
                        label = "${month}月",
                        inflow = points.sumOf { it.inflow },
                        outflow = points.sumOf { it.outflow },
                    )
                }
            StatsPeriod.WEEK -> dailyPoints.map { point ->
                StatsTrendUiPoint(
                    label = when (point.date.dayOfWeek.value) {
                        1 -> "一"
                        2 -> "二"
                        3 -> "三"
                        4 -> "四"
                        5 -> "五"
                        6 -> "六"
                        else -> "日"
                    },
                    inflow = point.inflow,
                    outflow = point.outflow,
                )
            }
            StatsPeriod.MONTH -> dailyPoints.map { point ->
                StatsTrendUiPoint(
                    label = "${point.date.dayOfMonth}",
                    inflow = point.inflow,
                    outflow = point.outflow,
                )
            }
        }
    }

    private fun List<com.shihuaidexianyu.money.domain.usecase.StatsAccountBalance>.toAccountUi(
        settings: AppSettings,
    ): List<StatsAccountUiModel> {
        val positiveTotal = sumOf { it.balance.coerceAtLeast(0L) }
        return map { account ->
            StatsAccountUiModel(
                accountId = account.accountId,
                name = account.name,
                colorName = account.colorName,
                balance = account.balance,
                balanceText = AmountFormatter.format(account.balance, settings),
                share = if (positiveTotal > 0L && account.balance > 0L) {
                    account.balance.toFloat() / positiveTotal
                } else {
                    0f
                },
            )
        }
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
