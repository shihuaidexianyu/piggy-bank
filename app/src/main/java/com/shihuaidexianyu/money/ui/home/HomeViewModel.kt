package com.shihuaidexianyu.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class DueReminderUiModel(
    val id: Long,
    val name: String,
    val type: ReminderType,
    val amountFormatted: String,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    // Defaulted so existing tests/previews constructing the model keep compiling.
    val accountName: String = "",
    val dueAt: Long = 0L,
)

data class StaleAccountUiModel(
    val accountId: Long,
    val name: String,
    val colorName: String,
    val currentBalance: Long,
    val lastBalanceUpdateAt: Long?,
)

data class HomeRecentRecordUiModel(
    val recordId: Long,
    val kind: HistoryRecordKind,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val occurredAt: Long,
    val isInvestmentAccount: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val retryToken: String? = null,
    val settings: PortableSettings = PortableSettings(),
    val totalAssets: Long = 0L,
    val hasAnyAccounts: Boolean = false,
    val allAccountCount: Int = 0,
    val periodRecordCount: Int = 0,
    val periodAssetChange: Long = 0,
    val periodCashInflow: Long = 0,
    val periodCashOutflow: Long = 0,
    val periodManualAdjustmentNet: Long = 0,
    val periodReconciliationNet: Long = 0,
    val staleAccountCount: Int = 0,
    val staleAccounts: List<StaleAccountUiModel> = emptyList(),
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val dueReminders: List<DueReminderUiModel> = emptyList(),
    val recentRecords: List<HomeRecentRecordUiModel> = emptyList(),
    /** Drives the switcher, so a tap highlights immediately. */
    val selectedPeriod: DashboardPeriod = DashboardPeriod.DEFAULT,
    /**
     * The period the aggregates below actually cover. Lags [selectedPeriod] by one snapshot, which
     * keeps the labels honest: a week label never sits above a month's numbers.
     */
    val period: DashboardPeriod = DashboardPeriod.DEFAULT,
    val hasInvestmentAccounts: Boolean = false,
    val investmentAssets: Long = 0L,
    val periodInvestmentPnl: Long = 0L,
)

internal fun HomeUiState.toAsyncContent(errorMessage: String = ""): AsyncContent<HomeUiState> {
    errorMessageRes?.let { return AsyncContent.Error(errorMessage, retryToken) }
    if (!hasCommittedContent) return AsyncContent.Loading
    if (isRefreshing) return AsyncContent.Refreshing(this)
    if (!hasAnyAccounts) return AsyncContent.Empty(EmptyKind.COMPLETELY_EMPTY)
    return AsyncContent.Data(this)
}

class HomeViewModel(
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedPeriod = DashboardPeriod.fromName(savedStateHandle[KEY_SELECTED_PERIOD]),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null
    private var retryGeneration = 0

    /**
     * Kept as its own flow rather than derived from [_uiState] so switching periods re-queries
     * without tearing down and restarting the whole dashboard subscription.
     */
    private val selectedPeriod = MutableStateFlow(
        DashboardPeriod.fromName(savedStateHandle[KEY_SELECTED_PERIOD]),
    )

    init {
        observeDashboard()
    }

    fun retry() {
        observeDashboard()
    }

    fun selectPeriod(period: DashboardPeriod) {
        if (selectedPeriod.value == period) return
        savedStateHandle[KEY_SELECTED_PERIOD] = period.name
        // Highlight the tab immediately; labels and numbers follow together on the next snapshot.
        _uiState.value = _uiState.value.copy(selectedPeriod = period, isRefreshing = true)
        selectedPeriod.value = period
    }

    private fun observeDashboard() {
        observationJob?.cancel()
        val hasCommittedContent = _uiState.value.hasCommittedContent
        _uiState.value = _uiState.value.copy(
            isLoading = !hasCommittedContent,
            isRefreshing = hasCommittedContent,
            errorMessageRes = null,
            retryToken = null,
        )
        observationJob = viewModelScope.launch {
            try {
                observeHomeDashboardUseCase(selectedPeriod)
                    .collect { snapshot ->
                    val staleAccountIds = snapshot.staleAccounts.map { it.id }.toSet()
                    val accountNames = snapshot.openAccounts.associate { it.id to it.name }
                    val investmentAccountIds = snapshot.openAccounts
                        .filter { it.isInvestment }
                        .map { it.id }
                        .toSet()
                    // copy() instead of a fresh HomeUiState: the selected period survives
                    // structurally instead of being hand-threaded —
                    // a fresh constructor call silently resets any field someone forgets to list.
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasCommittedContent = true,
                            errorMessageRes = null,
                            retryToken = null,
                            settings = snapshot.settings,
                            totalAssets = snapshot.totalAssets,
                            hasAnyAccounts = snapshot.hasAnyAccounts,
                            allAccountCount = snapshot.allAccountCount,
                            periodRecordCount = snapshot.periodRecordCount,
                            periodAssetChange = snapshot.periodBreakdown.assetChange,
                            periodCashInflow = snapshot.periodBreakdown.cashInflow,
                            periodCashOutflow = snapshot.periodBreakdown.cashOutflow,
                            periodManualAdjustmentNet = snapshot.periodBreakdown.manualAdjustmentNet,
                            periodReconciliationNet = snapshot.periodBreakdown.reconciliationNet,
                            staleAccountCount = snapshot.staleAccountCount,
                            staleAccounts = snapshot.staleAccounts.map { account ->
                                StaleAccountUiModel(
                                    accountId = account.id,
                                    name = account.name,
                                    colorName = account.colorName,
                                    currentBalance = snapshot.accountBalances[account.id] ?: 0L,
                                    lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                                )
                            },
                            accountOptions = snapshot.openAccounts.map { account ->
                                account.toAccountOptionUiModel(
                                    balance = snapshot.accountBalances[account.id] ?: 0L,
                                    isStale = account.id in staleAccountIds,
                                )
                            },
                            dueReminders = snapshot.dueReminders.map { reminder ->
                                DueReminderUiModel(
                                    id = reminder.id,
                                    name = reminder.name,
                                    type = ReminderType.fromValue(reminder.type),
                                    amountFormatted = AmountFormatter.format(
                                        reminder.amount,
                                        snapshot.settings,
                                    ),
                                    accountId = reminder.accountId,
                                    accountName = accountNames[reminder.accountId] ?: "—",
                                    dueAt = reminder.nextDueAt,
                                    direction = reminder.direction,
                                    amount = reminder.amount,
                                )
                            },
                            recentRecords = snapshot.recentRecords.map { record ->
                                val relatedAccountId = record.relatedAccountId
                                HomeRecentRecordUiModel(
                                    recordId = record.recordId,
                                    kind = record.type.toHomeRecordKind(),
                                    title = record.title,
                                    subtitle = if (record.type == HistoryRecordType.TRANSFER && relatedAccountId != null) {
                                        "${accountNames[record.accountId] ?: "—"} → ${accountNames[relatedAccountId] ?: "—"}"
                                    } else {
                                        accountNames[record.accountId] ?: "—"
                                    },
                                    amount = record.amount,
                                    occurredAt = record.occurredAt,
                                    isInvestmentAccount = record.accountId in investmentAccountIds,
                                )
                            },
                            period = snapshot.period,
                            hasInvestmentAccounts = snapshot.hasInvestmentAccounts,
                            investmentAssets = snapshot.investmentAssets,
                            periodInvestmentPnl = snapshot.periodInvestmentPnl,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("HomeViewModel", "Failed to observe home dashboard", e) }
                retryGeneration += 1
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessageRes = R.string.home_load_failed,
                    retryToken = "home:$retryGeneration",
                )
            }
        }
    }

    private fun HistoryRecordType.toHomeRecordKind(): HistoryRecordKind {
        return when (this) {
            HistoryRecordType.CASH_FLOW -> HistoryRecordKind.CASH_FLOW
            HistoryRecordType.TRANSFER -> HistoryRecordKind.TRANSFER
            HistoryRecordType.BALANCE_UPDATE -> HistoryRecordKind.BALANCE_UPDATE
            HistoryRecordType.BALANCE_ADJUSTMENT -> HistoryRecordKind.BALANCE_ADJUSTMENT
        }
    }

    private companion object {
        const val KEY_SELECTED_PERIOD = "home_selected_period"
    }
}
