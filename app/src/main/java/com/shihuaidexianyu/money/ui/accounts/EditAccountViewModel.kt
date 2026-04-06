package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class EditAccountUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isSaving: Boolean = false,
)

sealed interface EditAccountEffect {
    data object Saved : EditAccountEffect
    data object Archived : EditAccountEffect
    data object Closed : EditAccountEffect
    data class ShowMessage(
        override val message: String,
    ) : EditAccountEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditAccountViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val updateAccountUseCase: UpdateAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditAccountEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    emitClosedOnce()
                    return@launch
                }
                _uiState.value = EditAccountUiState(
                    isLoading = false,
                    name = account.name,
                    groupType = AccountGroupType.fromValue(account.groupType),
                    reminderConfig = accountReminderSettingsRepository.getReminderConfig(accountId),
                )
            } catch (_: Exception) {
                emitClosedOnce()
            }
        }
    }

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

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateAccountUseCase(
                    accountId = accountId,
                    name = state.name,
                    groupType = state.groupType,
                    balanceUpdateReminderConfig = state.reminderConfig,
                )
            }.onSuccess {
                effects.emit(EditAccountEffect.Saved)
            }.onFailure { error ->
                if (accountRepository.getAccountById(accountId) == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(EditAccountEffect.ShowMessage(error.message ?: "保存失败"))
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            runCatching {
                accountRepository.archiveAccount(accountId, System.currentTimeMillis())
            }.onSuccess {
                effects.emit(EditAccountEffect.Archived)
            }.onFailure { error ->
                if (accountRepository.getAccountById(accountId) == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                effects.emit(EditAccountEffect.ShowMessage(error.message ?: "归档失败"))
            }
        }
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditAccountEffect.Closed)
    }
}
