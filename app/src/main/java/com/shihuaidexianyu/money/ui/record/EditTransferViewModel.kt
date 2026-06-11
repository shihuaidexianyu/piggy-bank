package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
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
    val allFromAccountAmount: Long = 0L,
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
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditTransferUiState())
    val uiState: StateFlow<EditTransferUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditTransferEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false
    private var originalFromAccountId: Long? = null
    private var originalToAccountId: Long? = null
    private var originalAmount: Long = 0L

    init {
        viewModelScope.launch {
            try {
                val record = transactionRepository.queryTransferRecordById(recordId)
                if (record == null) {
                    emitDeletedOnce()
                    return@launch
                }
                originalFromAccountId = record.fromAccountId
                originalToAccountId = record.toAccountId
                originalAmount = record.amount
                val accounts = accountRepository.queryActiveAccounts()
                val nextState = EditTransferUiState(
                    isLoading = false,
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = calculateCurrentBalanceUseCase(account.id),
                        )
                    },
                    fromAccountId = record.fromAccountId,
                    toAccountId = record.toAccountId,
                    amountText = AmountFormatter.formatPlain(record.amount),
                    note = record.note,
                    occurredAtMillis = record.occurredAt,
                )
                _uiState.value = nextState.withAllFromAccountAmount()
            } catch (e: Exception) {
                android.util.Log.e("EditTransferViewModel", "Failed to load record", e)
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
    fun useAllFromAccountBalance() = updateState {
        if (allFromAccountAmount > 0L) {
            copy(amountText = AmountFormatter.formatPlain(allFromAccountAmount))
        } else {
            this
        }
    }
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
            val (fromId, toId) = runCatching { RecordValidator.requireTransferAccounts(state.fromAccountId, state.toAccountId) }
                .getOrElse { error -> effects.emit(EditTransferEffect.ShowMessage(error.userMessage("请选择账户"))); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(EditTransferEffect.ShowMessage(error.userMessage("请输入有效金额"))); return@launch }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error -> effects.emit(EditTransferEffect.ShowMessage(error.userMessage("时间不能晚于当前时间"))); return@launch }
            updateState { copy(isSaving = true) }
            runCatching {
                updateTransferRecordUseCase(
                    recordId = recordId,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = state.note,
                    occurredAt = state.occurredAtMillis,
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
            val record = transactionRepository.queryTransferRecordById(recordId)
            if (record == null) {
                emitDeletedOnce()
                return@launch
            }
            runCatching { deleteTransferRecordUseCase(recordId) }
                .onSuccess {
                    emitDeletedOnce()
                }
                .onFailure {
                    updateState { copy(showDeleteConfirm = false) }
                    effects.emit(EditTransferEffect.ShowMessage(it.message ?: "删除失败"))
                }
        }
    }

    private fun updateState(transform: EditTransferUiState.() -> EditTransferUiState) {
        _uiState.value = _uiState.value.transform().withAllFromAccountAmount()
    }

    private fun EditTransferUiState.withAllFromAccountAmount(): EditTransferUiState {
        val currentBalance = accounts.firstOrNull { it.id == fromAccountId }?.balance ?: 0L
        val balanceBeforeOriginalTransfer = when (fromAccountId) {
            originalFromAccountId -> currentBalance + originalAmount
            originalToAccountId -> currentBalance - originalAmount
            else -> currentBalance
        }
        return copy(allFromAccountAmount = balanceBeforeOriginalTransfer.coerceAtLeast(0L))
    }

    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditTransferEffect.Deleted)
    }
}
