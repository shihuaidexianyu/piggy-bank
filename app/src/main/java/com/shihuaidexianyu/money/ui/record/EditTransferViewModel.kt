package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class EditTransferUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditTransferEffect {
    data object Saved : EditTransferEffect
    data object Deleted : EditTransferEffect
    data class ShowMessage(
        override val message: String,
    ) : EditTransferEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditTransferViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditTransferUiState())
    val uiState: StateFlow<EditTransferUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditTransferEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val record = transactionRepository.queryTransferRecordById(recordId)
                if (record == null) {
                    emitDeletedOnce()
                    return@launch
                }
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.value = EditTransferUiState(
                    isLoading = false,
                    accounts = accounts.toAccountOptionUiModels(),
                    fromAccountId = record.fromAccountId,
                    toAccountId = record.toAccountId,
                    amountText = AmountFormatter.formatPlain(record.amount),
                    note = record.note,
                    occurredAtMillis = record.occurredAt,
                )
            } catch (_: Exception) {
                emitDeletedOnce()
            }
        }
    }

    fun updateFromAccount(accountId: Long) = updateState { copy(fromAccountId = accountId) }
    fun updateToAccount(accountId: Long) = updateState { copy(toAccountId = accountId) }
    fun swapAccounts() = updateState {
        copy(
            fromAccountId = toAccountId,
            toAccountId = fromAccountId,
        )
    }
    fun updateAmount(value: String) = updateState { copy(amountText = value) }
    fun updateNote(value: String) = updateState { copy(note = value) }
    fun updateOccurredAt(value: Long) = updateState {
        copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
    }
    fun showDeleteConfirm() = updateState { copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = updateState { copy(showDeleteConfirm = false) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val fromId = state.fromAccountId ?: run { effects.emit(EditTransferEffect.ShowMessage("请选择账户")); return@launch }
            val toId = state.toAccountId ?: run { effects.emit(EditTransferEffect.ShowMessage("请选择账户")); return@launch }
            val amount = com.shihuaidexianyu.money.util.AmountInputParser.parseToMinor(state.amountText) ?: run {
                effects.emit(EditTransferEffect.ShowMessage("金额不能为空"))
                return@launch
            }
            if (amount <= 0) {
                effects.emit(EditTransferEffect.ShowMessage("金额必须大于 0"))
                return@launch
            }
            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                effects.emit(EditTransferEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }
            updateState { copy(isSaving = true) }
            runCatching {
                updateTransferRecordUseCase(
                    recordId = recordId,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = occurredAt,
                )
            }.onSuccess { effects.emit(EditTransferEffect.Saved) }
                .onFailure {
                    if (transactionRepository.queryTransferRecordById(recordId) == null) {
                        emitDeletedOnce()
                        return@onFailure
                    }
                    updateState { copy(isSaving = false) }
                    effects.emit(EditTransferEffect.ShowMessage(it.message ?: "保存失败"))
                }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { deleteTransferRecordUseCase(recordId) }
                .onSuccess { emitDeletedOnce() }
                .onFailure {
                    updateState { copy(showDeleteConfirm = false) }
                    effects.emit(EditTransferEffect.ShowMessage(it.message ?: "删除失败"))
                }
        }
    }

    private fun updateState(transform: EditTransferUiState.() -> EditTransferUiState) {
        _uiState.value = _uiState.value.transform()
    }
    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditTransferEffect.Deleted)
    }
}
