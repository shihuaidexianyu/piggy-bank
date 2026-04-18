package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.RecordValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class EditReminderUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val type: ReminderType = ReminderType.MANUAL,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
    val amountText: String = "",
    val periodType: ReminderPeriodType = ReminderPeriodType.MONTHLY,
    val periodDay: String = "1",
    val periodMonth: String = "1",
    val periodCustomDays: String = "30",
    val isEnabled: Boolean = true,
    val isSaving: Boolean = false,
)

sealed interface EditReminderEffect {
    data object Saved : EditReminderEffect
    data object Closed : EditReminderEffect
    data class ShowMessage(override val message: String) : EditReminderEffect, UiEffect.HasMessage
}

class EditReminderViewModel(
    private val reminderId: Long,
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val updateReminderUseCase: UpdateReminderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditReminderUiState())
    val uiState: StateFlow<EditReminderUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditReminderEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val reminder = reminderRepository.getReminderById(reminderId)
                if (reminder == null) {
                    emitClosedOnce()
                    return@launch
                }
                val accounts = accountRepository.queryActiveAccounts()
                val periodType = ReminderPeriodType.fromValue(reminder.periodType)
                _uiState.value = EditReminderUiState(
                    isLoading = false,
                    name = reminder.name,
                    type = ReminderType.fromValue(reminder.type),
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = reminder.accountId,
                    direction = CashFlowDirection.fromValue(reminder.direction),
                    amountText = BigDecimal.valueOf(reminder.amount, 2)
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString(),
                    periodType = periodType,
                    periodDay = reminder.periodValue.toString(),
                    periodMonth = (reminder.periodMonth ?: 1).toString(),
                    periodCustomDays = if (periodType == ReminderPeriodType.CUSTOM_DAYS) {
                        reminder.periodValue.toString()
                    } else {
                        "30"
                    },
                    isEnabled = reminder.isEnabled,
                )
            } catch (e: Exception) {
                android.util.Log.e("EditReminderViewModel", "Failed to load reminder", e)
                emitClosedOnce()
            }
        }
    }

    fun updateName(value: String) { _uiState.value = _uiState.value.copy(name = value) }
    fun updateType(value: ReminderType) { _uiState.value = _uiState.value.copy(type = value) }
    fun updateAccount(id: Long) { _uiState.value = _uiState.value.copy(selectedAccountId = id) }
    fun updateDirection(value: CashFlowDirection) { _uiState.value = _uiState.value.copy(direction = value) }
    fun updateAmount(value: String) { _uiState.value = _uiState.value.copy(amountText = value) }
    fun updatePeriodType(value: ReminderPeriodType) { _uiState.value = _uiState.value.copy(periodType = value) }
    fun updatePeriodDay(value: String) { _uiState.value = _uiState.value.copy(periodDay = value) }
    fun updatePeriodMonth(value: String) { _uiState.value = _uiState.value.copy(periodMonth = value) }
    fun updatePeriodCustomDays(value: String) { _uiState.value = _uiState.value.copy(periodCustomDays = value) }
    fun updateEnabled(value: Boolean) { _uiState.value = _uiState.value.copy(isEnabled = value) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.message!!)); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.message!!)); return@launch }
            runCatching { RecordValidator.requireReminderName(state.name) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.message!!)); return@launch }

            val scheduleInput = parseReminderScheduleInput(
                periodType = state.periodType,
                periodDayText = state.periodDay,
                periodMonthText = state.periodMonth,
                periodCustomDaysText = state.periodCustomDays,
            ).getOrElse { error ->
                effects.emit(EditReminderEffect.ShowMessage(error.message ?: "请输入有效的周期"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateReminderUseCase(
                    reminderId = reminderId,
                    name = state.name,
                    type = state.type,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    periodType = state.periodType,
                    periodValue = scheduleInput.periodValue,
                    periodMonth = scheduleInput.periodMonth,
                    isEnabled = state.isEnabled,
                )
            }.onSuccess {
                effects.emit(EditReminderEffect.Saved)
            }.onFailure { throwable ->
                if (reminderRepository.getReminderById(reminderId) == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(EditReminderEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditReminderEffect.Closed)
    }
}
