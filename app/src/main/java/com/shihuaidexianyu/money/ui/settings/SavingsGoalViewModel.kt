package com.shihuaidexianyu.money.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.usecase.CreateSavingsGoalUseCase
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

data class SavingsGoalUiState(
    val isLoading: Boolean = true,
    val existingGoalId: Long? = null,
    val amountText: String = "",
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

sealed interface SavingsGoalEffect : UiEffect {
    data object Saved : SavingsGoalEffect
    data object Deleted : SavingsGoalEffect
    data class ShowMessage(override val message: String) : SavingsGoalEffect, UiEffect.HasMessage
}

class SavingsGoalViewModel(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val createSavingsGoalUseCase: CreateSavingsGoalUseCase,
    private val updateSavingsGoalUseCase: UpdateSavingsGoalUseCase,
    private val deleteSavingsGoalUseCase: DeleteSavingsGoalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SavingsGoalUiState())
    val uiState: StateFlow<SavingsGoalUiState> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<SavingsGoalEffect>(extraBufferCapacity = 1)
    val effectFlow: SharedFlow<SavingsGoalEffect> = _effectFlow.asSharedFlow()

    private var existingGoal: SavingsGoal? = null

    init {
        viewModelScope.launch {
            try {
                val goals = savingsGoalRepository.queryAll()
                val goal = goals.firstOrNull()
                if (goal != null) {
                    existingGoal = goal
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            existingGoalId = goal.id,
                            amountText = formatAmountText(goal.targetAmount),
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amountText = value) }
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

        val amount = AmountInputParser.parseToMinor(state.amountText)
        if (amount == null || amount <= 0L) {
            viewModelScope.launch { _effectFlow.emit(SavingsGoalEffect.ShowMessage("请输入有效的目标金额")) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val goal = existingGoal
                if (goal != null) {
                    updateSavingsGoalUseCase(goal.copy(targetAmount = amount))
                } else {
                    createSavingsGoalUseCase(targetAmount = amount)
                }
                _effectFlow.emit(SavingsGoalEffect.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _effectFlow.emit(SavingsGoalEffect.ShowMessage(e.message ?: "保存失败"))
            }
        }
    }

    fun delete() {
        val goal = existingGoal ?: return
        _uiState.update { it.copy(showDeleteConfirm = false, isSaving = true) }
        viewModelScope.launch {
            try {
                deleteSavingsGoalUseCase(goal.id)
                _effectFlow.emit(SavingsGoalEffect.Deleted)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _effectFlow.emit(SavingsGoalEffect.ShowMessage(e.message ?: "删除失败"))
            }
        }
    }

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
