package com.shihuaidexianyu.money.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
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
    val loadErrorMessage: String? = null,
    val hasGoal: Boolean = false,
    val amountText: String = "",
    val isSaving: Boolean = false,
    val showClearConfirm: Boolean = false,
)

sealed interface SavingsGoalEffect : UiEffect {
    data object Saved : SavingsGoalEffect
    data object Cleared : SavingsGoalEffect
    data class ShowMessage(override val message: String) : SavingsGoalEffect, UiEffect.HasMessage
}

class SavingsGoalViewModel(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val upsertSavingsGoalUseCase: UpsertSavingsGoalUseCase,
    private val clearSavingsGoalUseCase: ClearSavingsGoalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SavingsGoalUiState())
    val uiState: StateFlow<SavingsGoalUiState> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<SavingsGoalEffect>(extraBufferCapacity = 1)
    val effectFlow: SharedFlow<SavingsGoalEffect> = _effectFlow.asSharedFlow()
    private var observationJob: kotlinx.coroutines.Job? = null

    init {
        observeGoal()
    }

    fun retryLoad() {
        observeGoal()
    }

    private fun observeGoal() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
        observationJob = viewModelScope.launch {
            try {
                savingsGoalRepository.observe().collect { goal ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadErrorMessage = null,
                            hasGoal = goal != null,
                            amountText = goal?.let { value -> formatAmountText(value.targetAmount) }.orEmpty(),
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadErrorMessage = "净资产目标加载失败，请重试",
                    )
                }
            }
        }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amountText = value) }
    }

    fun showClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val amount = AmountInputParser.parseUnsignedToMinor(state.amountText)
        if (amount == null || amount <= 0L) {
            viewModelScope.launch { _effectFlow.emit(SavingsGoalEffect.ShowMessage("请输入有效的目标金额")) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching { upsertSavingsGoalUseCase(amount) }
                .onSuccess { _effectFlow.emit(SavingsGoalEffect.Saved) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _effectFlow.emit(
                        SavingsGoalEffect.ShowMessage(
                            error.message ?: if (state.hasGoal) "修改失败" else "设置失败",
                        ),
                    )
                }
        }
    }

    fun clear() {
        if (!_uiState.value.hasGoal || _uiState.value.isSaving) return
        _uiState.update { it.copy(showClearConfirm = false, isSaving = true) }
        viewModelScope.launch {
            runCatching { clearSavingsGoalUseCase() }
                .onSuccess { _effectFlow.emit(SavingsGoalEffect.Cleared) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _effectFlow.emit(SavingsGoalEffect.ShowMessage(error.message ?: "清除失败"))
                }
        }
    }

    private fun formatAmountText(amountInMinor: Long): String {
        val yuan = amountInMinor / 100L
        val fen = amountInMinor % 100L
        return if (fen == 0L) yuan.toString() else "$yuan.${fen.toString().padStart(2, '0')}"
    }
}
