package com.shihuaidexianyu.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val settings: PortableSettings = PortableSettings(),
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
)

class HomeViewModel(
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
    private val devicePreferencesRepository: DevicePreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    observeHomeDashboardUseCase(),
                    devicePreferencesRepository.observe(),
                ) { snapshot, devicePreferences -> snapshot to devicePreferences }
                    .collect { (snapshot, devicePreferences) ->
                    val visibility = AmountPrivacy.from(devicePreferences)
                        .visibilityFor(AmountSurface.IN_APP)
                    val staleAccountIds = snapshot.staleAccounts.map { it.id }.toSet()
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        settings = snapshot.settings,
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
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to observe home dashboard", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
