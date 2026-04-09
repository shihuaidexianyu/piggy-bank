package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.usecase.AccountShare
import com.shihuaidexianyu.money.domain.usecase.AssetGroupShare
import com.shihuaidexianyu.money.domain.usecase.CashFlowBar
import com.shihuaidexianyu.money.domain.usecase.CashFlowEvent
import com.shihuaidexianyu.money.domain.usecase.InvestmentPoint
import com.shihuaidexianyu.money.domain.usecase.InvestmentOverview
import com.shihuaidexianyu.money.domain.usecase.NetAssetPoint
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsUseCase
import com.shihuaidexianyu.money.domain.usecase.StatsIntervalSummary
import com.shihuaidexianyu.money.domain.usecase.StatsOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class CashFlowCardMode {
    CALENDAR,
    TREND,
}

enum class CashFlowGranularity(
    val label: String,
) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

enum class CashFlowDisplayUnit(
    val label: String,
) {
    AMOUNT("¥"),
    PERCENT("%"),
}

data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val settings: AppSettings = AppSettings(),
    val overview: StatsOverview = StatsOverview(
        totalInflow = 0L,
        totalOutflow = 0L,
        netCashFlow = 0L,
        currentNetAssets = 0L,
        netAssetDelta = 0L,
        activeAccountCount = 0,
        activeInvestmentAccountCount = 0,
    ),
    val intervals: List<StatsIntervalSummary> = emptyList(),
    val assetGroupShares: List<AssetGroupShare> = emptyList(),
    val topAccountShares: List<AccountShare> = emptyList(),
    val investmentOverview: InvestmentOverview = InvestmentOverview(
        totalPnl = 0L,
        weightedReturnRate = null,
        netTransferIn = 0L,
        netTransferOut = 0L,
        settlementCount = 0,
    ),
    val cashFlowCardMode: CashFlowCardMode = CashFlowCardMode.CALENDAR,
    val cashFlowGranularity: CashFlowGranularity = CashFlowGranularity.DAY,
    val cashFlowDisplayUnit: CashFlowDisplayUnit = CashFlowDisplayUnit.AMOUNT,
    val cashFlowSelectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val cashFlowVisibleEpochDay: Long = YearMonth.now().atDay(1).toEpochDay(),
    val cashFlowEvents: List<CashFlowEvent> = emptyList(),
    val cashFlowBars: List<CashFlowBar> = emptyList(),
    val netAssetPoints: List<NetAssetPoint> = emptyList(),
    val investmentPoints: List<InvestmentPoint> = emptyList(),
)

class StatsViewModel(
    private val observeStatsUseCase: ObserveStatsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _periodFlow = MutableStateFlow(StatsPeriod.MONTH)

    init {
        viewModelScope.launch {
            observeStatsUseCase.invoke(_periodFlow).collect { snapshot ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    isLoading = false,
                    period = snapshot.period,
                    settings = snapshot.settings,
                    overview = snapshot.overview,
                    intervals = snapshot.intervals,
                    assetGroupShares = snapshot.assetGroupShares,
                    topAccountShares = snapshot.topAccountShares,
                    investmentOverview = snapshot.investmentOverview,
                    cashFlowEvents = snapshot.cashFlowEvents,
                    cashFlowBars = snapshot.cashFlowBars,
                    netAssetPoints = snapshot.netAssetPoints,
                    investmentPoints = snapshot.investmentPoints,
                )
            }
        }
    }

    fun updatePeriod(period: StatsPeriod) {
        _periodFlow.value = period
        _uiState.value = _uiState.value.copy(period = period, isLoading = true)
    }

    fun updateCashFlowCardMode(mode: CashFlowCardMode) {
        _uiState.value = _uiState.value.copy(cashFlowCardMode = mode)
    }

    fun updateCashFlowGranularity(granularity: CashFlowGranularity) {
        val selectedDate = LocalDate.ofEpochDay(_uiState.value.cashFlowSelectedEpochDay)
        _uiState.value = _uiState.value.copy(
            cashFlowGranularity = granularity,
            cashFlowVisibleEpochDay = selectedDate.withDayOfMonth(1).toEpochDay(),
        )
    }

    fun updateCashFlowDisplayUnit(unit: CashFlowDisplayUnit) {
        _uiState.value = _uiState.value.copy(cashFlowDisplayUnit = unit)
    }

    fun selectCashFlowDate(epochDay: Long) {
        val selectedDate = LocalDate.ofEpochDay(epochDay)
        _uiState.value = _uiState.value.copy(
            cashFlowSelectedEpochDay = epochDay,
            cashFlowVisibleEpochDay = selectedDate.withDayOfMonth(1).toEpochDay(),
        )
    }

    fun shiftCashFlowVisiblePeriod(offset: Long) {
        val state = _uiState.value
        val current = LocalDate.ofEpochDay(state.cashFlowVisibleEpochDay)
        val shifted = when (state.cashFlowGranularity) {
            CashFlowGranularity.DAY,
            CashFlowGranularity.WEEK,
            -> current.plusMonths(offset)
            CashFlowGranularity.MONTH -> current.plusYears(offset)
            CashFlowGranularity.YEAR -> current.plusYears(offset * 12L)
        }
        val selectedDate = LocalDate.ofEpochDay(state.cashFlowSelectedEpochDay)
        val remappedSelected = remapSelectedDate(
            selectedDate = selectedDate,
            targetVisibleDate = shifted,
            granularity = state.cashFlowGranularity,
        )
        _uiState.value = state.copy(
            cashFlowVisibleEpochDay = shifted.withDayOfMonth(1).toEpochDay(),
            cashFlowSelectedEpochDay = remappedSelected.toEpochDay(),
        )
    }

    private fun remapSelectedDate(
        selectedDate: LocalDate,
        targetVisibleDate: LocalDate,
        granularity: CashFlowGranularity,
    ): LocalDate {
        return when (granularity) {
            CashFlowGranularity.DAY,
            CashFlowGranularity.WEEK,
            -> {
                val yearMonth = YearMonth.from(targetVisibleDate)
                val day = selectedDate.dayOfMonth.coerceAtMost(yearMonth.lengthOfMonth())
                yearMonth.atDay(day)
            }
            CashFlowGranularity.MONTH -> {
                targetVisibleDate
                    .withMonth(selectedDate.monthValue.coerceIn(1, 12))
                    .withDayOfMonth(1)
            }
            CashFlowGranularity.YEAR -> {
                targetVisibleDate.withMonth(1).withDayOfMonth(1)
            }
        }
    }
}
