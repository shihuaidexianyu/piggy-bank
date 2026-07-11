package com.shihuaidexianyu.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
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
)

data class StatsAccountCashFlowUiModel(
    val accountId: Long,
    val name: String,
    val inflowText: String,
    val outflowText: String,
    val inflowHistoryFilters: HistoryRecordFilters,
    val outflowHistoryFilters: HistoryRecordFilters,
)

data class StatsTransferPathUiModel(
    val label: String,
    val amountText: String,
    val historyFilters: HistoryRecordFilters,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    val hasSourceAccounts: Boolean = false,
    val errorMessage: String? = null,
    val retryToken: String? = null,
    val settings: PortableSettings = PortableSettings(),
    val rangeStartInclusive: Long = 0L,
    val rangeEndExclusive: Long = 0L,
    val rangeText: String = "",
    val canNavigateNext: Boolean = false,
    val totalInflow: Long = 0L,
    val totalOutflow: Long = 0L,
    val netCashFlow: Long = 0L,
    val totalInflowText: String = "",
    val totalOutflowText: String = "",
    val netCashFlowText: String = "",
    val inflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val outflowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val netCashFlowHistoryFilters: HistoryRecordFilters = HistoryRecordFilters(),
    val dailyPoints: List<StatsDailyUiModel> = emptyList(),
    val accountCashFlows: List<StatsAccountCashFlowUiModel> = emptyList(),
    val transferPaths: List<StatsTransferPathUiModel> = emptyList(),
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
            errorMessage = null,
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
                    errorMessage = "分析加载失败，请重试",
                    retryToken = "stats:$retryGeneration",
                )
            }
        }
    }

    private fun StatsDashboardSnapshot.toUiState(visibility: AmountVisibility): StatsUiState {
        val selectedMonth = YearMonth.from(Instant.ofEpochMilli(range.startInclusive).atZone(zoneId))
        val currentMonth = YearMonth.from(Instant.ofEpochMilli(clockProvider.nowMillis()).atZone(zoneId))
        fun amount(value: Long): String = AmountFormatter.format(value, settings, visibility)
        fun signed(value: Long): String = if (value > 0L && visibility != AmountVisibility.MASKED) "+${amount(value)}" else amount(value)
        return StatsUiState(
            isLoading = false,
            hasCommittedContent = true,
            hasSourceAccounts = hasSourceAccounts,
            settings = settings,
            rangeStartInclusive = range.startInclusive,
            rangeEndExclusive = range.endExclusive,
            rangeText = DateTimeFormatter.ofPattern("yyyy年M月").format(selectedMonth),
            canNavigateNext = selectedMonth < currentMonth,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashFlow = netCashFlow,
            totalInflowText = amount(totalInflow),
            totalOutflowText = amount(totalOutflow),
            netCashFlowText = signed(netCashFlow),
            inflowHistoryFilters = inflowHistoryFilters,
            outflowHistoryFilters = outflowHistoryFilters,
            netCashFlowHistoryFilters = netCashFlowHistoryFilters,
            dailyPoints = dailyPoints.map { point ->
                StatsDailyUiModel(
                    date = point.date,
                    dateText = DateTimeFormatter.ofPattern("M月d日").format(point.date),
                    inflowText = amount(point.inflow),
                    outflowText = amount(point.outflow),
                    netFlowText = signed(point.netFlow),
                    historyFilters = point.historyFilters,
                )
            },
            accountCashFlows = accountCashFlows.map { flow ->
                StatsAccountCashFlowUiModel(
                    accountId = flow.accountId,
                    name = flow.name,
                    inflowText = amount(flow.inflow),
                    outflowText = amount(flow.outflow),
                    inflowHistoryFilters = flow.inflowHistoryFilters,
                    outflowHistoryFilters = flow.outflowHistoryFilters,
                )
            },
            transferPaths = transferPaths.map { path ->
                StatsTransferPathUiModel(
                    label = "${path.fromAccountName} → ${path.toAccountName}",
                    amountText = amount(path.amount),
                    historyFilters = path.historyFilters,
                )
            },
        )
    }
}
