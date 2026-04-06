package com.shihuaidexianyu.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val totalAssets: Long = 0,
    val periodNetInflow: Long = 0,
    val periodNetOutflow: Long = 0,
    val staleAccountCount: Int = 0,
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val dueReminders: List<DueReminderUiModel> = emptyList(),
)

class HomeViewModel(
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                observeHomeDashboardUseCase().collect { snapshot ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        settings = snapshot.settings,
                        totalAssets = snapshot.totalAssets,
                        periodNetInflow = snapshot.periodNetInflow,
                        periodNetOutflow = snapshot.periodNetOutflow,
                        staleAccountCount = snapshot.staleAccountCount,
                        accountOptions = snapshot.activeAccounts.toAccountOptionUiModels(),
                        dueReminders = snapshot.dueReminders.map { reminder ->
                            DueReminderUiModel(
                                id = reminder.id,
                                name = reminder.name,
                                type = ReminderType.fromValue(reminder.type),
                                amountFormatted = AmountFormatter.format(reminder.amount, snapshot.settings),
                                accountId = reminder.accountId,
                                direction = reminder.direction,
                                amount = reminder.amount,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

