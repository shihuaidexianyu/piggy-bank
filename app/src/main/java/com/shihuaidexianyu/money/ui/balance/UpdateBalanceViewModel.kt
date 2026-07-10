package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class UpdateBalanceUiState(
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val actualBalanceText: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val systemBalanceBeforeUpdate: Long = 0,
    val actualBalancePreview: Long? = null,
    val deltaPreview: Long? = null,
    val actualBalanceEdited: Boolean = false,
    val isSaving: Boolean = false,
    val latestResult: UpdateBalanceResult? = null,
)

sealed interface UpdateBalanceEffect {
    data object Saved : UpdateBalanceEffect
    data class ShowMessage(
        override val message: String,
    ) : UpdateBalanceEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class UpdateBalanceViewModel(
    initialAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateBalanceUiState(selectedAccountId = initialAccountId))
    val uiState: StateFlow<UpdateBalanceUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<UpdateBalanceEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryActiveAccounts()
                val selected = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = selected,
                )
                refreshPreview(resetActualBalanceToSystem = true)
            } catch (e: Exception) {
                android.util.Log.e("UpdateBalanceViewModel", "Failed to load accounts", e)
            }
        }
    }

    fun updateAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            actualBalanceEdited = false,
        )
        refreshPreview(resetActualBalanceToSystem = true)
    }

    fun updateActualBalance(value: String) {
        val actual = AmountInputParser.parseSignedToMinor(value)
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        _uiState.value = _uiState.value.copy(
            actualBalanceText = value,
            actualBalancePreview = actual,
            deltaPreview = actual?.minus(systemBalance),
            actualBalanceEdited = true,
        )
    }

    fun updateOccurredAt(value: Long) {
        val shouldResetActualBalance = !_uiState.value.actualBalanceEdited
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
        refreshPreview(resetActualBalanceToSystem = shouldResetActualBalance)
    }

    fun resetActualBalanceToSystem() {
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        _uiState.value = _uiState.value.copy(
            actualBalanceText = AmountFormatter.formatPlain(systemBalance),
            actualBalancePreview = systemBalance,
            deltaPreview = 0,
            actualBalanceEdited = false,
        )
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(UpdateBalanceEffect.ShowMessage(error.userMessage("请选择账户"))); return@launch }
            val actualBalance = runCatching { RecordValidator.requireSignedAmount(state.actualBalanceText) }
                .getOrElse { error -> effects.emit(UpdateBalanceEffect.ShowMessage(error.userMessage("请输入有效金额"))); return@launch }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error -> effects.emit(UpdateBalanceEffect.ShowMessage(error.userMessage("时间不能晚于当前时间"))); return@launch }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateBalanceUseCase(
                    accountId = accountId,
                    actualBalance = actualBalance,
                    occurredAt = state.occurredAtMillis,
                )
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    latestResult = result,
                    actualBalanceText = AmountFormatter.formatPlain(result.actualBalance),
                    systemBalanceBeforeUpdate = result.actualBalance,
                    actualBalancePreview = result.actualBalance,
                    deltaPreview = 0,
                    actualBalanceEdited = false,
                )
                effects.emit(UpdateBalanceEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(UpdateBalanceEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }

    private fun refreshPreview(resetActualBalanceToSystem: Boolean) {
        val snapshot = _uiState.value
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val systemBalance = snapshot.selectedAccountId
                ?.let { calculateCurrentBalanceUseCase(it, snapshot.occurredAtMillis) }
                ?: 0L
            val current = _uiState.value
            if (current.selectedAccountId != snapshot.selectedAccountId ||
                current.occurredAtMillis != snapshot.occurredAtMillis
            ) {
                return@launch
            }

            val actualBalanceText = if (resetActualBalanceToSystem) {
                AmountFormatter.formatPlain(systemBalance)
            } else {
                current.actualBalanceText
            }
            val actualBalancePreview = if (resetActualBalanceToSystem) {
                systemBalance
            } else {
                AmountInputParser.parseSignedToMinor(actualBalanceText)
            }

            _uiState.value = current.copy(
                actualBalanceText = actualBalanceText,
                systemBalanceBeforeUpdate = systemBalance,
                actualBalancePreview = actualBalancePreview,
                deltaPreview = actualBalancePreview?.minus(systemBalance),
                actualBalanceEdited = if (resetActualBalanceToSystem) false else current.actualBalanceEdited,
            )
        }
    }
}
