package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReminderUiModel(
    val id: Long,
    val name: String,
    val type: ReminderType,
    val amountFormatted: String,
    val periodDescription: String,
    val nextDueFormatted: String,
    val isEnabled: Boolean,
    val isOverdue: Boolean,
    val accountId: Long,
    val direction: String,
    val amount: Long,
)

data class BalanceReminderUiModel(
    val accountId: Long,
    val name: String,
    val currentBalanceFormatted: String,
    val lastBalanceUpdateText: String,
)

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val balanceReminders: List<BalanceReminderUiModel> = emptyList(),
    val reminders: List<ReminderUiModel> = emptyList(),
)

class ReminderListViewModel(
    private val reminderRepository: RecurringReminderRepository,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderListUiState())
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.observeAllReminders(),
                observeHomeDashboardUseCase(),
            ) { reminders, snapshot -> reminders to snapshot }
                .collect { (reminders, snapshot) ->
                    _uiState.value = ReminderListUiState(
                        isLoading = false,
                        balanceReminders = snapshot.staleAccounts.map { account ->
                            BalanceReminderUiModel(
                                accountId = account.id,
                                name = account.name,
                                currentBalanceFormatted = AmountFormatter.format(
                                    snapshot.accountBalances[account.id] ?: 0L,
                                    snapshot.settings,
                                ),
                                lastBalanceUpdateText = account.lastBalanceUpdateAt?.let {
                                    "最近核对 ${DateTimeTextFormatter.format(it)}"
                                } ?: "尚未核对",
                            )
                        },
                        reminders = reminders.map { it.toUiModel(snapshot.settings) },
                    )
                }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            deleteReminderUseCase(id)
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

internal fun RecurringReminder.toUiModel(settings: AppSettings): ReminderUiModel {
    val periodType = ReminderPeriodType.fromValue(this.periodType)
    val periodDesc = when (periodType) {
        ReminderPeriodType.MONTHLY -> "每月${periodValue}日"
        ReminderPeriodType.YEARLY -> "每年${periodMonth ?: 1}月${periodValue}日"
        ReminderPeriodType.CUSTOM_DAYS -> "每${periodValue}天"
    }
    val nextDate = Instant.ofEpochMilli(nextDueAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return ReminderUiModel(
        id = id,
        name = name,
        type = ReminderType.fromValue(type),
        amountFormatted = AmountFormatter.format(amount, settings),
        periodDescription = periodDesc,
        nextDueFormatted = nextDate.format(dateFormatter),
        isEnabled = isEnabled,
        isOverdue = isEnabled && nextDueAt <= System.currentTimeMillis(),
        accountId = accountId,
        direction = direction,
        amount = amount,
    )
}
