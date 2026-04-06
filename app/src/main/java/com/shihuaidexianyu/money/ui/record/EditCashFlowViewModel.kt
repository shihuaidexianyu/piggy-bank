package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
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

data class EditCashFlowUiState(
    val isLoading: Boolean = true,
    val direction: CashFlowDirection = CashFlowDirection.INFLOW,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val amountText: String = "",
    val purpose: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditCashFlowEffect {
    data object Saved : EditCashFlowEffect
    data object Deleted : EditCashFlowEffect
    data class ShowMessage(
        override val message: String,
    ) : EditCashFlowEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditCashFlowViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val updateCashFlowRecordUseCase: UpdateCashFlowRecordUseCase,
    private val deleteCashFlowRecordUseCase: DeleteCashFlowRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditCashFlowUiState())
    val uiState: StateFlow<EditCashFlowUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditCashFlowEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val record = transactionRepository.queryCashFlowRecordById(recordId)
                if (record == null) {
                    emitDeletedOnce()
                    return@launch
                }
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.value = EditCashFlowUiState(
                    isLoading = false,
                    direction = CashFlowDirection.fromValue(record.direction),
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = record.accountId,
                    amountText = AmountFormatter.formatPlain(record.amount),
                    purpose = record.purpose,
                    occurredAtMillis = record.occurredAt,
                )
            } catch (_: Exception) {
                emitDeletedOnce()
            }
        }
    }

    fun updateAccount(accountId: Long) = updateState { copy(selectedAccountId = accountId) }
    fun updateAmount(value: String) = updateState { copy(amountText = value) }
    fun updatePurpose(value: String) = updateState { copy(purpose = value) }
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
            val accountId = state.selectedAccountId ?: run {
                effects.emit(EditCashFlowEffect.ShowMessage("请选择账户"))
                return@launch
            }
            val amount = com.shihuaidexianyu.money.util.AmountInputParser.parseToMinor(state.amountText) ?: run {
                effects.emit(EditCashFlowEffect.ShowMessage("金额不能为空"))
                return@launch
            }
            if (amount <= 0) {
                effects.emit(EditCashFlowEffect.ShowMessage("金额必须大于 0"))
                return@launch
            }
            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                effects.emit(EditCashFlowEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }

            updateState { copy(isSaving = true) }
            runCatching {
                updateCashFlowRecordUseCase(
                    recordId = recordId,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    purpose = state.purpose,
                    occurredAt = occurredAt,
                )
            }.onSuccess {
                effects.emit(EditCashFlowEffect.Saved)
            }.onFailure {
                if (transactionRepository.queryCashFlowRecordById(recordId) == null) {
                    emitDeletedOnce()
                    return@onFailure
                }
                updateState { copy(isSaving = false) }
                effects.emit(EditCashFlowEffect.ShowMessage(it.message ?: "保存失败"))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching {
                deleteCashFlowRecordUseCase(recordId)
            }.onSuccess {
                emitDeletedOnce()
            }.onFailure {
                updateState { copy(showDeleteConfirm = false) }
                effects.emit(EditCashFlowEffect.ShowMessage(it.message ?: "删除失败"))
            }
        }
    }

    private fun updateState(transform: EditCashFlowUiState.() -> EditCashFlowUiState) {
        _uiState.value = _uiState.value.transform()
    }
    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditCashFlowEffect.Deleted)
    }
}
