package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.util.AmountFormatter
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
)

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val reminders: List<ReminderUiModel> = emptyList(),
)

class ReminderListViewModel(
    private val reminderRepository: RecurringReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteReminderUseCase: DeleteReminderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderListUiState())
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.observeAllReminders(),
                settingsRepository.observeSettings(),
            ) { reminders, settings -> reminders to settings }
                .collect { (reminders, settings) ->
                    _uiState.value = ReminderListUiState(
                        isLoading = false,
                        reminders = reminders.map { it.toUiModel(settings) },
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

internal fun RecurringReminderEntity.toUiModel(settings: AppSettings): ReminderUiModel {
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
    )
}
