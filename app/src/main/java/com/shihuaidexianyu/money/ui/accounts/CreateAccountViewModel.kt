package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
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
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
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
        _uiState.value = _uiState.value.copy(name = value.take(MAX_ACCOUNT_NAME_LENGTH))
    }

    fun updateColorName(value: String) {
        _uiState.value = _uiState.value.copy(colorName = normalizeAccountColorName(value))
    }

    fun updateIconName(value: String) {
        _uiState.value = _uiState.value.copy(iconName = normalizeAccountIconName(value))
    }

    fun updateReminderPeriod(value: BalanceUpdateReminderPeriod) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(period = value),
        )
    }

    fun updateReminderWeekday(value: BalanceUpdateReminderWeekday) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(weekday = value),
        )
    }

    fun updateReminderMonthDay(value: Int) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(monthDay = value),
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
            val amount = AmountInputParser.parseSignedToMinor(_uiState.value.amountText)
            if (amount == null) {
                effects.emit(CreateAccountEffect.ShowMessage("金额不能为空"))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSaving = true)
            runCatching {
                createAccountUseCase(
                    name = _uiState.value.name,
                    initialBalance = amount,
                    balanceUpdateReminderConfig = _uiState.value.reminderConfig,
                    colorName = _uiState.value.colorName,
                    iconName = _uiState.value.iconName,
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
