package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditBalanceUpdateUiState(
    val isLoading: Boolean = true,
    val accountName: String = "",
    val actualBalanceText: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val systemBalanceBeforeUpdate: Long = 0,
    val actualBalancePreview: Long? = null,
    val deltaPreview: Long? = null,
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EditBalanceUpdateEffect {
    data object Saved : EditBalanceUpdateEffect
    data object Deleted : EditBalanceUpdateEffect
    data class ShowMessage(
        override val message: String,
    ) : EditBalanceUpdateEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditBalanceUpdateViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val updateBalanceUpdateRecordUseCase: UpdateBalanceUpdateRecordUseCase,
    private val deleteBalanceUpdateRecordUseCase: DeleteBalanceUpdateRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditBalanceUpdateUiState())
    val uiState: StateFlow<EditBalanceUpdateUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditBalanceUpdateEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    private var accountId: Long = 0
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val record = transactionRepository.getBalanceUpdateRecordById(recordId)
                if (record == null) {
                    emitDeletedOnce()
                    return@launch
                }
                val account = accountRepository.getAccountById(record.accountId)
                accountId = record.accountId
                _uiState.value = EditBalanceUpdateUiState(
                    isLoading = false,
                    accountName = account?.name ?: "未知账户",
                    actualBalanceText = AmountFormatter.formatPlain(record.actualBalance),
                    occurredAtMillis = record.occurredAt,
                    systemBalanceBeforeUpdate = record.systemBalanceBeforeUpdate,
                    actualBalancePreview = record.actualBalance,
                    deltaPreview = record.delta,
                )
            } catch (_: Exception) {
                emitDeletedOnce()
            }
        }
    }

    fun updateActualBalance(value: String) {
        val actualBalance = AmountInputParser.parseToMinor(value)
        _uiState.value = _uiState.value.copy(
            actualBalanceText = value,
            actualBalancePreview = actualBalance,
            deltaPreview = actualBalance?.minus(_uiState.value.systemBalanceBeforeUpdate),
        )
    }

    fun updateOccurredAt(value: Long) {
        val occurredAt = DateTimeTextFormatter.floorToMinute(value)
        _uiState.value = _uiState.value.copy(occurredAtMillis = occurredAt)
        if (accountId <= 0) return

        viewModelScope.launch {
            val context = resolveBalanceUpdateContextUseCase(
                accountId = accountId,
                occurredAt = occurredAt,
                excludingRecordId = recordId,
            )
            val actualBalance = AmountInputParser.parseToMinor(_uiState.value.actualBalanceText)
            _uiState.value = _uiState.value.copy(
                systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                actualBalancePreview = actualBalance,
                deltaPreview = actualBalance?.minus(context.systemBalanceBeforeUpdate),
            )
        }
    }

    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val actualBalance = AmountInputParser.parseToMinor(state.actualBalanceText)
            if (actualBalance == null) {
                effects.emit(EditBalanceUpdateEffect.ShowMessage("金额不能为空"))
                return@launch
            }
            if (state.occurredAtMillis > System.currentTimeMillis()) {
                effects.emit(EditBalanceUpdateEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateBalanceUpdateRecordUseCase(
                    recordId = recordId,
                    actualBalance = actualBalance,
                    occurredAt = state.occurredAtMillis,
                )
            }.onSuccess {
                effects.emit(EditBalanceUpdateEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(EditBalanceUpdateEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching {
                deleteBalanceUpdateRecordUseCase(recordId)
            }.onSuccess {
                emitDeletedOnce()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
                effects.emit(EditBalanceUpdateEffect.ShowMessage(throwable.message ?: "撤销失败"))
            }
        }
    }

    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditBalanceUpdateEffect.Deleted)
    }
}
