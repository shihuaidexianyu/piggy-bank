package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
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
                val accountIds = accounts.map { it.id }.toSet()
                val fromAccountId = _uiState.value.fromAccountId
                    ?.takeIf { it in accountIds }
                    ?: accounts.firstOrNull()?.id
                val toAccountId = _uiState.value.toAccountId
                    ?.takeIf { it in accountIds && it != fromAccountId }
                    ?: accounts.firstOrNull { it.id != fromAccountId }?.id
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.toAccountOptionUiModels(),
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                )
            } catch (e: Exception) {
                android.util.Log.e("RecordTransferViewModel", "Failed to load accounts", e)
            }
        }
    }

    fun updateFromAccount(accountId: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            fromAccountId = accountId,
            toAccountId = state.toAccountId
                ?.takeIf { it != accountId }
                ?: state.accounts.firstOrNull { it.id != accountId }?.id,
        )
    }

    fun updateToAccount(accountId: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            fromAccountId = state.fromAccountId
                ?.takeIf { it != accountId }
                ?: state.accounts.firstOrNull { it.id != accountId }?.id,
            toAccountId = accountId,
        )
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
            val (fromId, toId) = runCatching { RecordValidator.requireTransferAccounts(state.fromAccountId, state.toAccountId) }
                .getOrElse { error -> effects.emit(RecordTransferEffect.ShowMessage(error.message!!)); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(RecordTransferEffect.ShowMessage(error.message!!)); return@launch }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error -> effects.emit(RecordTransferEffect.ShowMessage(error.message!!)); return@launch }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                createTransferRecordUseCase(
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = state.occurredAtMillis,
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
