package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateSavingsGoalUseCase
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

data class EditSavingsGoalUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val name: String = "",
    val amountText: String = "",
    val accounts: List<SavingsGoalAccountUiModel> = emptyList(),
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditSavingsGoalEffect : UiEffect {
    data object Saved : EditSavingsGoalEffect
    data object Deleted : EditSavingsGoalEffect
    data object Closed : EditSavingsGoalEffect
    data class ShowMessage(override val message: String) : EditSavingsGoalEffect, UiEffect.HasMessage
}

class EditSavingsGoalViewModel(
    private val goalId: Long,
    private val accountRepository: AccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val updateSavingsGoalUseCase: UpdateSavingsGoalUseCase,
    private val deleteSavingsGoalUseCase: DeleteSavingsGoalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditSavingsGoalUiState())
    val uiState: StateFlow<EditSavingsGoalUiState> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<EditSavingsGoalEffect>(extraBufferCapacity = 1)
    val effectFlow: SharedFlow<EditSavingsGoalEffect> = _effectFlow.asSharedFlow()

    private var existingGoal: SavingsGoal? = null

    init {
        viewModelScope.launch {
            try {
                val goal = savingsGoalRepository.getGoalById(goalId)
                if (goal == null) {
                    _uiState.update { it.copy(isLoading = false, isMissing = true) }
                    _effectFlow.emit(EditSavingsGoalEffect.Closed)
                    return@launch
                }
                existingGoal = goal
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = goal.name,
                        amountText = formatAmountText(goal.targetAmount),
                        accounts = accounts.map { account ->
                            account.toUiModel(goal.accountIds.contains(account.id))
                        },
                    )
                }
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

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val goal = existingGoal ?: return

        val amount = AmountInputParser.parseToMinor(state.amountText)
        if (amount == null || amount <= 0L) {
            viewModelScope.launch { _effectFlow.emit(EditSavingsGoalEffect.ShowMessage("请输入有效的目标金额")) }
            return
        }
        if (state.name.isBlank()) {
            viewModelScope.launch { _effectFlow.emit(EditSavingsGoalEffect.ShowMessage("请输入目标名称")) }
            return
        }

        val selectedAccountIds = state.accounts.filter { it.isSelected }.map { it.id }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                updateSavingsGoalUseCase(
                    goal.copy(
                        name = state.name,
                        targetAmount = amount,
                        accountIds = selectedAccountIds,
                    ),
                )
                _effectFlow.emit(EditSavingsGoalEffect.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _effectFlow.emit(EditSavingsGoalEffect.ShowMessage(e.message ?: "保存失败"))
            }
        }
    }

    fun delete() {
        _uiState.update { it.copy(showDeleteConfirm = false, isSaving = true) }
        viewModelScope.launch {
            try {
                deleteSavingsGoalUseCase(goalId)
                _effectFlow.emit(EditSavingsGoalEffect.Deleted)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _effectFlow.emit(EditSavingsGoalEffect.ShowMessage(e.message ?: "删除失败"))
            }
        }
    }

    private fun Account.toUiModel(selected: Boolean): SavingsGoalAccountUiModel =
        SavingsGoalAccountUiModel(
            id = id,
            name = name,
            colorName = colorName,
            iconName = iconName,
            balance = initialBalance,
            isSelected = selected,
        )

    private fun formatAmountText(amountInMinor: Long): String {
        val absAmount = kotlin.math.abs(amountInMinor)
        val yuan = absAmount / 100L
        val fen = absAmount % 100L
        return if (fen == 0L) {
            yuan.toString()
        } else {
            "$yuan.${fen.toString().padStart(2, '0')}"
        }
    }
}
