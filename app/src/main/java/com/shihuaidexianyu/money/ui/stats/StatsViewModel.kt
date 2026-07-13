package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.StatsDashboardSnapshot
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StatsDailyUiModel(
    val date: LocalDate,
    val dateText: String,
    val inflowText: String,
    val outflowText: String,
    val netFlowText: String,
    val historyFilters: HistoryRecordFilters,
    val inflow: Long = 0L,
    val outflow: Long = 0L,
    val netFlow: Long = 0L,
    val inflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val outflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
)

data class StatsAccountCashFlowUiModel(
    val accountId: Long,
    val name: String,
    val inflowText: String,
    val outflowText: String,
    val inflowHistoryFilters: HistoryRecordFilters,
    val outflowHistoryFilters: HistoryRecordFilters,
    val inflow: Long = 0L,
    val outflow: Long = 0L,
    val netFlow: Long = 0L,
    val netFlowText: String = "",
)

data class StatsTransferPathUiModel(
    val label: String,
    val amountText: String,
    val historyFilters: HistoryRecordFilters,
    val amount: Long = 0L,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    val hasSourceAccounts: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val retryToken: String? = null,
    val settings: PortableSettings = PortableSettings(),
    val rangeStartInclusive: Long = 0L,
    val rangeEndExclusive: Long = 0L,
    val rangeText: String = "",
    val canNavigateNext: Boolean = false,
    val openingAssets: Long = 0L,
    val closingAssets: Long = 0L,
    val assetChange: Long = 0L,
    val assetAdjustment: Long = 0L,
    val totalInflow: Long = 0L,
    val totalOutflow: Long = 0L,
    val netCashFlow: Long = 0L,
    val totalInflowText: String = "",
    val totalOutflowText: String = "",
    val netCashFlowText: String = "",
    val openingAssetsText: String = "",
    val closingAssetsText: String = "",
    val assetChangeText: String = "",
    val assetAdjustmentText: String = "",
    val inflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val outflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val netCashFlowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val dailyPoints: List<StatsDailyUiModel> = emptyList(),
    val accountCashFlows: List<StatsAccountCashFlowUiModel> = emptyList(),
    val transferPaths: List<StatsTransferPathUiModel> = emptyList(),
    val totalTransfer: Long = 0L,
    val totalTransferText: String = "",
)

internal fun StatsUiState.toAsyncContent(errorMessage: String = ""): AsyncContent<StatsUiState> {
    errorMessageRes?.let { return AsyncContent.Error(errorMessage, retryToken) }
    if (!hasCommittedContent) return AsyncContent.Loading
    if (isRefreshing) return AsyncContent.Refreshing(this)
    if (!hasSourceAccounts) return AsyncContent.Empty(EmptyKind.COMPLETELY_EMPTY)
    return AsyncContent.Data(this)
}

class StatsViewModel(
    private val observeStatsDashboardUseCase: ObserveStatsDashboardUseCase,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(currentSelection())
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var retryGeneration = 0

    init {
        observeStats()
    }

    fun retry() = observeStats()

    fun moveToPreviousRange() = moveMonth(-1L)

    fun moveToNextRange() {
        val zoneId = zoneIdProvider.zoneId()
        val selectedMonth = selectedMonth(zoneId)
        val currentMonth = YearMonth.from(Instant.ofEpochMilli(clockProvider.nowMillis()).atZone(zoneId))
        if (selectedMonth < currentMonth) moveMonth(1L)
    }

    fun resetToCurrentRange() {
        selectedRange.value = currentSelection()
    }

    private fun currentSelection(): StatsRangeSelection = StatsRangeSelection(
        period = StatsPeriod.MONTH,
        anchorMillis = clockProvider.nowMillis(),
    )

    private fun moveMonth(amount: Long) {
        val zoneId = zoneIdProvider.zoneId()
        val shifted = Instant.ofEpochMilli(selectedRange.value.anchorMillis)
            .atZone(zoneId)
            .plusMonths(amount)
        selectedRange.value = StatsRangeSelection(StatsPeriod.MONTH, shifted.toInstant().toEpochMilli())
    }

    private fun selectedMonth(zoneId: java.time.ZoneId): YearMonth = YearMonth.from(
        Instant.ofEpochMilli(selectedRange.value.anchorMillis).atZone(zoneId),
    )

    private fun observeStats() {
        observationJob?.cancel()
        val committed = _uiState.value.hasCommittedContent
        _uiState.value = _uiState.value.copy(
            isLoading = !committed,
            isRefreshing = committed,
            errorMessageRes = null,
            retryToken = null,
        )
        observationJob = viewModelScope.launch {
            try {
                combine(
                    observeStatsDashboardUseCase(selectedRange),
                    devicePreferencesRepository.observe(),
                ) { snapshot, preferences -> snapshot to AmountPrivacy.from(preferences).visibilityFor(AmountSurface.IN_APP) }
                    .collect { (snapshot, visibility) -> _uiState.value = snapshot.toUiState(visibility) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runCatching { android.util.Log.e("StatsViewModel", "Failed to observe stats", error) }
                retryGeneration += 1
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessageRes = R.string.stats_load_failed,
                    retryToken = "stats:$retryGeneration",
                )
            }
        }
    }

    private fun StatsDashboardSnapshot.toUiState(visibility: AmountVisibility): StatsUiState {
        val selectedMonth = YearMonth.from(Instant.ofEpochMilli(range.startInclusive).atZone(zoneId))
        val currentMonth = YearMonth.from(Instant.ofEpochMilli(clockProvider.nowMillis()).atZone(zoneId))
        val totalTransfer = transferPaths.map { it.amount }.ledgerSumExact()
        val assetChange = ledgerSubtractExact(closingAssets, openingAssets)
        fun amount(value: Long): String = AmountFormatter.format(value, settings, visibility)
        fun signed(value: Long): String = if (value > 0L && visibility != AmountVisibility.MASKED) "+${amount(value)}" else amount(value)
        return StatsUiState(
            isLoading = false,
            hasCommittedContent = true,
            hasSourceAccounts = hasSourceAccounts,
            settings = settings,
            rangeStartInclusive = range.startInclusive,
            rangeEndExclusive = range.endExclusive,
            rangeText = DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE).format(selectedMonth),
            canNavigateNext = selectedMonth < currentMonth,
            openingAssets = openingAssets,
            closingAssets = closingAssets,
            assetChange = assetChange,
            assetAdjustment = assetAdjustment,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            totalInflowText = amount(totalInflow),
            totalOutflowText = amount(totalOutflow),
            netCashFlowText = signed(netCashFlow),
            openingAssetsText = amount(openingAssets),
            closingAssetsText = amount(closingAssets),
            assetChangeText = signed(assetChange),
            assetAdjustmentText = signed(assetAdjustment),
            inflowHistoryFilters = inflowHistoryFilters,
            outflowHistoryFilters = outflowHistoryFilters,
            netCashFlowHistoryFilters = netCashFlowHistoryFilters,
            dailyPoints = dailyPoints.map { point ->
                StatsDailyUiModel(
                    date = point.date,
                    dateText = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE).format(point.date),
                    inflow = point.inflow,
                    outflow = point.outflow,
                    netFlow = point.netFlow,
                    inflowText = amount(point.inflow),
                    outflowText = amount(point.outflow),
                    netFlowText = signed(point.netFlow),
                    historyFilters = point.historyFilters,
                    inflowHistoryFilters = point.inflowHistoryFilters,
                    outflowHistoryFilters = point.outflowHistoryFilters,
                )
            },
            accountCashFlows = accountCashFlows.map { flow ->
                StatsAccountCashFlowUiModel(
                    accountId = flow.accountId,
                    name = flow.name,
                    inflow = flow.inflow,
                    outflow = flow.outflow,
                    netFlow = ledgerSubtractExact(flow.inflow, flow.outflow),
                    inflowText = amount(flow.inflow),
                    outflowText = amount(flow.outflow),
                    netFlowText = signed(ledgerSubtractExact(flow.inflow, flow.outflow)),
                    inflowHistoryFilters = flow.inflowHistoryFilters,
                    outflowHistoryFilters = flow.outflowHistoryFilters,
                )
            },
            transferPaths = transferPaths.map { path ->
                StatsTransferPathUiModel(
                    label = "${path.fromAccountName} → ${path.toAccountName}",
                    amount = path.amount,
                    amountText = amount(path.amount),
                    historyFilters = path.historyFilters,
                )
            },
            totalTransfer = totalTransfer,
            totalTransferText = amount(totalTransfer),
        )
    }
}
