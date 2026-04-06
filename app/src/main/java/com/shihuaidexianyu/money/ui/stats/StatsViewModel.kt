package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.usecase.AccountShare
import com.shihuaidexianyu.money.domain.usecase.CashFlowBar
import com.shihuaidexianyu.money.domain.usecase.InvestmentPoint
import com.shihuaidexianyu.money.domain.usecase.NetAssetPoint
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val settings: AppSettings = AppSettings(),
    val cashFlowBars: List<CashFlowBar> = emptyList(),
    val netAssetPoints: List<NetAssetPoint> = emptyList(),
    val accountShares: List<AccountShare> = emptyList(),
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
                _uiState.value = StatsUiState(
                    isLoading = false,
                    period = snapshot.period,
                    settings = snapshot.settings,
                    cashFlowBars = snapshot.cashFlowBars,
                    netAssetPoints = snapshot.netAssetPoints,
                    accountShares = snapshot.accountShares,
                    investmentPoints = snapshot.investmentPoints,
                )
            }
        }
    }

    fun updatePeriod(period: StatsPeriod) {
        _periodFlow.value = period
        _uiState.value = _uiState.value.copy(period = period, isLoading = true)
    }
}
