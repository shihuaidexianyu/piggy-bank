package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class CreateAccountUiState(
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val amountText: String = "",
    val isSaving: Boolean = false,
)

sealed interface CreateAccountEffect {
    data object Saved : CreateAccountEffect
    data class ShowMessage(
        override val message: String,
    ) : CreateAccountEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class CreateAccountViewModel(
    private val createAccountUseCase: CreateAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<CreateAccountEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun updateGroupType(value: AccountGroupType) {
        _uiState.value = _uiState.value.copy(groupType = value)
    }

    fun updateReminderWeekday(value: BalanceUpdateReminderWeekday) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(weekday = value),
        )
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(hour = hour, minute = minute),
        )
    }

    fun updateAmountText(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value)
    }

    fun save() {
        viewModelScope.launch {
            val amount = AmountInputParser.parseToMinor(_uiState.value.amountText)
            if (amount == null) {
                effects.emit(CreateAccountEffect.ShowMessage("金额不能为空"))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSaving = true)
            runCatching {
                createAccountUseCase(
                    name = _uiState.value.name,
                    groupType = _uiState.value.groupType,
                    initialBalance = amount,
                    balanceUpdateReminderConfig = _uiState.value.reminderConfig,
                )
            }.onSuccess {
                effects.emit(CreateAccountEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(CreateAccountEffect.ShowMessage(throwable.message ?: "创建账户失败"))
            }
        }
    }
}
