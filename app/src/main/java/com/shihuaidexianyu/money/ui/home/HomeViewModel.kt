package com.shihuaidexianyu.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.SavingsGoalProgress
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.BudgetPace
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.MonthlyBudgetStatus
import com.shihuaidexianyu.money.domain.usecase.PeriodDelta
import com.shihuaidexianyu.money.domain.usecase.calculatePeriodDelta
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val monthlyBudget: MonthlyBudgetStatus? = null,
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
    val savingsGoalProgress: SavingsGoalProgress? = null,
    /** Drives the switcher, so a tap highlights immediately. */
    val selectedPeriod: DashboardPeriod = DashboardPeriod.DEFAULT,
    /**
     * The period the aggregates below actually cover. Lags [selectedPeriod] by one snapshot, which
     * keeps the labels honest: a week label never sits above a month's numbers.
     */
    val period: DashboardPeriod = DashboardPeriod.DEFAULT,
    val netWorthDelta: PeriodDelta = calculatePeriodDelta(0L, 0L),
    val cashInflowDelta: PeriodDelta = calculatePeriodDelta(0L, 0L),
    val cashOutflowDelta: PeriodDelta = calculatePeriodDelta(0L, 0L),
    val budgetPace: BudgetPace? = null,
    val hasInvestmentAccounts: Boolean = false,
    val investmentAssets: Long = 0L,
    val periodInvestmentPnl: Long = 0L,
    val showMonthlyBudgetEditor: Boolean = false,
    val monthlyBudgetInput: String = "",
    @param:StringRes val monthlyBudgetInputErrorRes: Int? = null,
    @param:StringRes val monthlyBudgetSaveErrorRes: Int? = null,
    val isMonthlyBudgetSaving: Boolean = false,
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
    private val observeSavingsGoalUseCase: ObserveSavingsGoalUseCase,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedPeriod = DashboardPeriod.fromName(savedStateHandle[KEY_SELECTED_PERIOD]),
            showMonthlyBudgetEditor = savedStateHandle[KEY_BUDGET_EDITOR_OPEN] ?: false,
            monthlyBudgetInput = savedStateHandle.get<String>(KEY_BUDGET_INPUT).orEmpty(),
            monthlyBudgetInputErrorRes = savedStateHandle.get<Int>(KEY_BUDGET_INPUT_ERROR),
            monthlyBudgetSaveErrorRes = savedStateHandle.get<Int>(KEY_BUDGET_SAVE_ERROR),
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
                combine(
                    observeHomeDashboardUseCase(selectedPeriod),
                    devicePreferencesRepository.observe(),
                    observeSavingsGoalUseCase(),
                ) { snapshot, devicePreferences, savingsGoalProgress ->
                    Triple(snapshot, devicePreferences, savingsGoalProgress)
                }
                    .collect { (snapshot, devicePreferences, savingsGoalProgress) ->
                    val visibility = AmountPrivacy.from(devicePreferences)
                        .visibilityFor(AmountSurface.IN_APP)
                    val staleAccountIds = snapshot.staleAccounts.map { it.id }.toSet()
                    val accountNames = snapshot.openAccounts.associate { it.id to it.name }
                    val investmentAccountIds = snapshot.openAccounts
                        .filter { it.isInvestment }
                        .map { it.id }
                        .toSet()
                    // copy() instead of a fresh HomeUiState: unrelated state (budget editor,
                    // selected period) survives structurally instead of by being hand-threaded —
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
                            monthlyBudget = snapshot.monthlyBudget,
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
                                        visibility,
                                    ),
                                    accountId = reminder.accountId,
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
                            savingsGoalProgress = savingsGoalProgress,
                            period = snapshot.period,
                            netWorthDelta = snapshot.netWorthDelta,
                            cashInflowDelta = snapshot.cashInflowDelta,
                            cashOutflowDelta = snapshot.cashOutflowDelta,
                            budgetPace = snapshot.budgetPace,
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

    fun openMonthlyBudgetEditor() {
        clearPendingBudgetAction()
        val input = _uiState.value.settings.monthlyBudgetAmount?.toEditableAmount().orEmpty()
        updateBudgetEditor(
            show = true,
            input = input,
            inputErrorRes = null,
            saveErrorRes = null,
        )
    }

    fun dismissMonthlyBudgetEditor() {
        if (_uiState.value.isMonthlyBudgetSaving) return
        updateBudgetEditor(show = false)
    }

    fun updateMonthlyBudgetInput(value: String) {
        if (_uiState.value.isMonthlyBudgetSaving) return
        clearPendingBudgetAction()
        updateBudgetEditor(
            input = value,
            inputErrorRes = null,
            saveErrorRes = null,
        )
    }

    fun saveMonthlyBudget() {
        val current = _uiState.value
        if (current.isMonthlyBudgetSaving) return
        val amount = AmountInputParser.parseUnsignedToMinor(current.monthlyBudgetInput)
        if (amount == null || amount <= 0L) {
            clearPendingBudgetAction()
            updateBudgetEditor(inputErrorRes = R.string.home_budget_positive_error, saveErrorRes = null)
            return
        }
        persistMonthlyBudget(BudgetPendingAction.SET, amount)
    }

    fun retryMonthlyBudgetSave() {
        if (_uiState.value.isMonthlyBudgetSaving) return
        val pendingAction = savedStateHandle.get<String>(KEY_BUDGET_PENDING_ACTION)?.let { value ->
            runCatching { BudgetPendingAction.valueOf(value) }.getOrNull()
        }
        when (pendingAction) {
            BudgetPendingAction.SET -> {
                val amount = savedStateHandle.get<Long>(KEY_BUDGET_PENDING_AMOUNT)
                    ?: return saveMonthlyBudget()
                persistMonthlyBudget(BudgetPendingAction.SET, amount)
            }
            BudgetPendingAction.CLOSE -> persistMonthlyBudget(BudgetPendingAction.CLOSE, null)
            null -> saveMonthlyBudget()
        }
    }

    fun closeMonthlyBudget() {
        if (_uiState.value.isMonthlyBudgetSaving) return
        persistMonthlyBudget(BudgetPendingAction.CLOSE, null)
    }

    private fun persistMonthlyBudget(action: BudgetPendingAction, amount: Long?) {
        savedStateHandle[KEY_BUDGET_PENDING_ACTION] = action.name
        if (amount == null) {
            savedStateHandle.remove<Long>(KEY_BUDGET_PENDING_AMOUNT)
        } else {
            savedStateHandle[KEY_BUDGET_PENDING_AMOUNT] = amount
        }
        savedStateHandle.remove<String>(KEY_BUDGET_SAVE_ERROR)
        _uiState.value = _uiState.value.copy(
            isMonthlyBudgetSaving = true,
            monthlyBudgetInputErrorRes = null,
            monthlyBudgetSaveErrorRes = null,
        )
        savedStateHandle[KEY_BUDGET_INPUT_ERROR] = null
        viewModelScope.launch {
            try {
                portableSettingsRepository.updateMonthlyBudgetAmount(amount)
                clearPendingBudgetAction()
                updateBudgetEditor(
                    show = false,
                    input = "",
                    inputErrorRes = null,
                    saveErrorRes = null,
                    saving = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateBudgetEditor(
                    show = true,
                    saveErrorRes = R.string.home_budget_save_failed,
                    saving = false,
                )
            }
        }
    }

    private fun updateBudgetEditor(
        show: Boolean = _uiState.value.showMonthlyBudgetEditor,
        input: String = _uiState.value.monthlyBudgetInput,
        @StringRes inputErrorRes: Int? = _uiState.value.monthlyBudgetInputErrorRes,
        @StringRes saveErrorRes: Int? = _uiState.value.monthlyBudgetSaveErrorRes,
        saving: Boolean = _uiState.value.isMonthlyBudgetSaving,
    ) {
        savedStateHandle[KEY_BUDGET_EDITOR_OPEN] = show
        savedStateHandle[KEY_BUDGET_INPUT] = input
        savedStateHandle[KEY_BUDGET_INPUT_ERROR] = inputErrorRes
        if (saveErrorRes == null) {
            savedStateHandle.remove<String>(KEY_BUDGET_SAVE_ERROR)
        } else {
            savedStateHandle[KEY_BUDGET_SAVE_ERROR] = saveErrorRes
        }
        _uiState.value = _uiState.value.copy(
            showMonthlyBudgetEditor = show,
            monthlyBudgetInput = input,
            monthlyBudgetInputErrorRes = inputErrorRes,
            monthlyBudgetSaveErrorRes = saveErrorRes,
            isMonthlyBudgetSaving = saving,
        )
    }

    private fun Long.toEditableAmount(): String = BigDecimal.valueOf(this, 2)
        .stripTrailingZeros()
        .toPlainString()

    private fun clearPendingBudgetAction() {
        savedStateHandle.remove<String>(KEY_BUDGET_PENDING_ACTION)
        savedStateHandle.remove<Long>(KEY_BUDGET_PENDING_AMOUNT)
        savedStateHandle.remove<String>(KEY_BUDGET_SAVE_ERROR)
    }

    private fun HistoryRecordType.toHomeRecordKind(): HistoryRecordKind {
        return when (this) {
            HistoryRecordType.CASH_FLOW -> HistoryRecordKind.CASH_FLOW
            HistoryRecordType.TRANSFER -> HistoryRecordKind.TRANSFER
            HistoryRecordType.BALANCE_UPDATE -> HistoryRecordKind.BALANCE_UPDATE
            HistoryRecordType.BALANCE_ADJUSTMENT -> HistoryRecordKind.BALANCE_ADJUSTMENT
        }
    }

    private enum class BudgetPendingAction {
        SET,
        CLOSE,
    }

    private companion object {
        const val KEY_BUDGET_EDITOR_OPEN = "home_budget_editor_open"
        const val KEY_BUDGET_INPUT = "home_budget_input"
        const val KEY_BUDGET_INPUT_ERROR = "home_budget_input_error"
        const val KEY_BUDGET_SAVE_ERROR = "home_budget_save_error"
        const val KEY_BUDGET_PENDING_ACTION = "home_budget_pending_action"
        const val KEY_BUDGET_PENDING_AMOUNT = "home_budget_pending_amount"
        const val KEY_SELECTED_PERIOD = "home_selected_period"
    }
}
