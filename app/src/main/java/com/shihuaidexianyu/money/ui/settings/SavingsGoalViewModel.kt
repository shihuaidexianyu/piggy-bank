package com.shihuaidexianyu.money.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.SavingsGoalProgress
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavingsGoalUiState(
    val isLoading: Boolean = true,
    val loadErrorMessageRes: Int? = null,
    val hasGoal: Boolean = false,
    val amountText: String = "",
    val isSaving: Boolean = false,
    val showClearConfirm: Boolean = false,
    val progress: SavingsGoalProgress? = null,
)

sealed interface SavingsGoalEffect : UiEffect {
    data object Saved : SavingsGoalEffect
    data object Cleared : SavingsGoalEffect
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : SavingsGoalEffect, UiEffect.HasMessage
}

class SavingsGoalViewModel(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val observeSavingsGoalUseCase: ObserveSavingsGoalUseCase,
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
        _uiState.update { it.copy(isLoading = true, loadErrorMessageRes = null) }
        observationJob = viewModelScope.launch {
            try {
                combine(
                    savingsGoalRepository.observe(),
                    observeSavingsGoalUseCase(),
                ) { goal, progress -> goal to progress }
                    .collect { (goal, progress) ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                loadErrorMessageRes = null,
                                hasGoal = goal != null,
                                amountText = goal?.let { value -> formatAmountText(value.targetAmount) }.orEmpty(),
                                progress = progress,
                            )
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadErrorMessageRes = R.string.goal_load_failed,
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
            viewModelScope.launch { _effectFlow.emit(SavingsGoalEffect.ShowMessage("", messageRes = R.string.goal_amount_invalid)) }
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
                            error.message.orEmpty(),
                            messageRes = if (state.hasGoal) R.string.goal_edit_failed else R.string.goal_set_failed,
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
                    _effectFlow.emit(SavingsGoalEffect.ShowMessage(error.message.orEmpty(), messageRes = R.string.goal_clear_failed))
                }
        }
    }

    private fun formatAmountText(amountInMinor: Long): String {
        val yuan = amountInMinor / 100L
        val fen = amountInMinor % 100L
        return if (fen == 0L) yuan.toString() else "$yuan.${fen.toString().padStart(2, '0')}"
    }
}
