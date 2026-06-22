package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceAdjustmentDetailUiState(
    val isLoading: Boolean = true,
    val accountName: String = "",
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val isDeleting: Boolean = false,
)

sealed interface BalanceAdjustmentDetailEffect {
    data object Closed : BalanceAdjustmentDetailEffect
    data object Deleted : BalanceAdjustmentDetailEffect
    data class ShowMessage(
        override val message: String,
    ) : BalanceAdjustmentDetailEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BalanceAdjustmentDetailViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val deleteBalanceAdjustmentUseCase: DeleteBalanceAdjustmentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceAdjustmentDetailUiState())
    val uiState: StateFlow<BalanceAdjustmentDetailUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<BalanceAdjustmentDetailEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                transactionRepository.observeChangeVersion().collect {
                    loadRecord()
                }
            } catch (e: Exception) {
                android.util.Log.e("BalanceAdjustmentDetailViewModel", "Failed to observe record", e)
                emitClosedOnce()
            }
        }
    }

    fun delete() {
        if (closed) return
        if (_uiState.value.isDeleting) return
        _uiState.value = _uiState.value.copy(isDeleting = true)
        viewModelScope.launch {
            try {
                deleteBalanceAdjustmentUseCase(recordId)
                emitDeletedOnce()
            } catch (e: Exception) {
                android.util.Log.e("BalanceAdjustmentDetailViewModel", "Failed to delete adjustment", e)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                effects.emit(BalanceAdjustmentDetailEffect.ShowMessage(e.message ?: "删除失败"))
            }
        }
    }

    private suspend fun loadRecord() {
        val record = transactionRepository.getBalanceAdjustmentRecordById(recordId)
        if (record == null) {
            emitClosedOnce()
            return
        }
        val account = accountRepository.getAccountById(record.accountId)
        _uiState.value = BalanceAdjustmentDetailUiState(
            isLoading = false,
            accountName = account?.name ?: "未知账户",
            delta = record.delta,
            occurredAt = record.occurredAt,
        )
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(BalanceAdjustmentDetailEffect.Closed)
    }

    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(BalanceAdjustmentDetailEffect.Deleted)
    }
}

