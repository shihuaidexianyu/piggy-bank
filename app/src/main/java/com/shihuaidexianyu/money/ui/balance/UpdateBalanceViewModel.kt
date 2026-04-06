package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
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

    init {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryActiveAccounts()
                val selected = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id
                val systemBalance = selected?.let { calculateCurrentBalanceUseCase(it) } ?: 0L
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = selected,
                    actualBalanceText = AmountFormatter.formatPlain(systemBalance),
                    systemBalanceBeforeUpdate = systemBalance,
                    actualBalancePreview = systemBalance,
                    deltaPreview = 0,
                )
            } catch (_: Exception) {
                // leave current state as-is
            }
        }
    }

    fun updateAccount(accountId: Long) {
        viewModelScope.launch {
            val systemBalance = calculateCurrentBalanceUseCase(accountId)
            _uiState.value = _uiState.value.copy(
                selectedAccountId = accountId,
                actualBalanceText = AmountFormatter.formatPlain(systemBalance),
                systemBalanceBeforeUpdate = systemBalance,
                actualBalancePreview = systemBalance,
                deltaPreview = 0,
            )
        }
    }

    fun updateActualBalance(value: String) {
        val actual = AmountInputParser.parseToMinor(value)
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        _uiState.value = _uiState.value.copy(
            actualBalanceText = value,
            actualBalancePreview = actual,
            deltaPreview = actual?.minus(systemBalance),
        )
    }

    fun updateOccurredAt(value: Long) {
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = state.selectedAccountId
            if (accountId == null) {
                effects.emit(UpdateBalanceEffect.ShowMessage("请选择账户"))
                return@launch
            }

            val actualBalance = AmountInputParser.parseToMinor(state.actualBalanceText)
            if (actualBalance == null) {
                effects.emit(UpdateBalanceEffect.ShowMessage("金额不能为空"))
                return@launch
            }

            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                effects.emit(UpdateBalanceEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateBalanceUseCase(
                    accountId = accountId,
                    actualBalance = actualBalance,
                    occurredAt = occurredAt,
                )
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    latestResult = result,
                    actualBalanceText = AmountFormatter.formatPlain(result.actualBalance),
                    systemBalanceBeforeUpdate = result.actualBalance,
                    actualBalancePreview = result.actualBalance,
                    deltaPreview = 0,
                )
                effects.emit(UpdateBalanceEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(UpdateBalanceEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }
}
