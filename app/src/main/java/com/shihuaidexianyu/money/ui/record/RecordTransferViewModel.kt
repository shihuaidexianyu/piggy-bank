package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class RecordTransferUiState(
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val isSaving: Boolean = false,
)

sealed interface RecordTransferEffect {
    data object Saved : RecordTransferEffect
    data class ShowMessage(
        override val message: String,
    ) : RecordTransferEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class RecordTransferViewModel(
    initialFromAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordTransferUiState(fromAccountId = initialFromAccountId))
    val uiState: StateFlow<RecordTransferUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<RecordTransferEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.toAccountOptionUiModels(),
                    fromAccountId = _uiState.value.fromAccountId ?: accounts.firstOrNull()?.id,
                    toAccountId = accounts.firstOrNull { it.id != _uiState.value.fromAccountId }?.id,
                )
            } catch (_: Exception) {
                // leave current state as-is
            }
        }
    }

    fun updateFromAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(fromAccountId = accountId)
    }

    fun updateToAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(toAccountId = accountId)
    }

    fun swapAccounts() {
        val state = _uiState.value
        _uiState.value = state.copy(
            fromAccountId = state.toAccountId,
            toAccountId = state.fromAccountId,
        )
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value)
    }

    fun updateNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value)
    }

    fun updateOccurredAt(value: Long) {
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val fromId = state.fromAccountId
            val toId = state.toAccountId
            if (fromId == null || toId == null) {
                effects.emit(RecordTransferEffect.ShowMessage("请选择账户"))
                return@launch
            }

            val amount = AmountInputParser.parseToMinor(state.amountText)
            if (amount == null) {
                effects.emit(RecordTransferEffect.ShowMessage("金额不能为空"))
                return@launch
            }
            if (amount <= 0) {
                effects.emit(RecordTransferEffect.ShowMessage("金额必须大于 0"))
                return@launch
            }

            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                effects.emit(RecordTransferEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                createTransferRecordUseCase(
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = occurredAt,
                )
            }.onSuccess {
                effects.emit(RecordTransferEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(RecordTransferEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }
}
