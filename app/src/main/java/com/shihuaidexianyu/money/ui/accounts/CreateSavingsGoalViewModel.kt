package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CreateSavingsGoalUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavingsGoalAccountUiModel(
    val id: Long,
    val name: String,
    val colorName: String,
    val iconName: String,
    val balance: Long,
    val isSelected: Boolean,
)

data class CreateSavingsGoalUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val amountText: String = "",
    val accounts: List<SavingsGoalAccountUiModel> = emptyList(),
    val isSaving: Boolean = false,
)

sealed interface CreateSavingsGoalEffect : UiEffect {
    data class Saved(val goalId: Long) : CreateSavingsGoalEffect
    data class ShowMessage(override val message: String) : CreateSavingsGoalEffect, UiEffect.HasMessage
}

class CreateSavingsGoalViewModel(
    private val accountRepository: AccountRepository,
    private val createSavingsGoalUseCase: CreateSavingsGoalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateSavingsGoalUiState())
    val uiState: StateFlow<CreateSavingsGoalUiState> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<CreateSavingsGoalEffect>(extraBufferCapacity = 1)
    val effectFlow: SharedFlow<CreateSavingsGoalEffect> = _effectFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.update { it.copy(isLoading = false, accounts = accounts.map { account -> account.toUiModel() }) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amountText = value) }
    }

    fun toggleAccount(accountId: Long) {
        _uiState.update { state ->
            state.copy(
                accounts = state.accounts.map { account ->
                    if (account.id == accountId) account.copy(isSelected = !account.isSelected) else account
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val amount = AmountInputParser.parseToMinor(state.amountText)
        if (amount == null || amount <= 0L) {
            viewModelScope.launch { _effectFlow.emit(CreateSavingsGoalEffect.ShowMessage("请输入有效的目标金额")) }
            return
        }
        if (state.name.isBlank()) {
            viewModelScope.launch { _effectFlow.emit(CreateSavingsGoalEffect.ShowMessage("请输入目标名称")) }
            return
        }

        val selectedAccountIds = state.accounts.filter { it.isSelected }.map { it.id }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val goalId = createSavingsGoalUseCase(
                    name = state.name,
                    targetAmount = amount,
                    accountIds = selectedAccountIds,
                )
                _effectFlow.emit(CreateSavingsGoalEffect.Saved(goalId))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _effectFlow.emit(CreateSavingsGoalEffect.ShowMessage(e.message ?: "保存失败"))
            }
        }
    }

    private fun Account.toUiModel(): SavingsGoalAccountUiModel =
        SavingsGoalAccountUiModel(
            id = id,
            name = name,
            colorName = colorName,
            iconName = iconName,
            balance = initialBalance,
            isSelected = false,
        )
}
