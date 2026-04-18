package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
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

data class CreateReminderUiState(
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

sealed interface CreateReminderEffect {
    data object Saved : CreateReminderEffect
    data class ShowMessage(override val message: String) : CreateReminderEffect, UiEffect.HasMessage
}

class CreateReminderViewModel(
    private val accountRepository: AccountRepository,
    private val createReminderUseCase: CreateReminderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateReminderUiState())
    val uiState: StateFlow<CreateReminderUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<CreateReminderEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.queryActiveAccounts()
            _uiState.value = _uiState.value.copy(
                accounts = accounts.toAccountOptionUiModels(),
                selectedAccountId = accounts.firstOrNull()?.id,
            )
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

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.message!!)); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.message!!)); return@launch }
            runCatching { RecordValidator.requireReminderName(state.name) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.message!!)); return@launch }

            val scheduleInput = parseReminderScheduleInput(
                periodType = state.periodType,
                periodDayText = state.periodDay,
                periodMonthText = state.periodMonth,
                periodCustomDaysText = state.periodCustomDays,
            ).getOrElse { error ->
                effects.emit(CreateReminderEffect.ShowMessage(error.message ?: "请输入有效的周期"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                createReminderUseCase(
                    name = state.name,
                    type = state.type,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    periodType = state.periodType,
                    periodValue = scheduleInput.periodValue,
                    periodMonth = scheduleInput.periodMonth,
                )
            }.onSuccess {
                effects.emit(CreateReminderEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(CreateReminderEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }
}
