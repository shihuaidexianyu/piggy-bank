package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceUpdateDetailUiState(
    val recordId: Long = 0,
    val accountId: Long = 0,
    val accountName: String = "",
    val actualBalance: Long = 0,
    val systemBalanceBeforeUpdate: Long = 0,
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
)

sealed interface BalanceUpdateDetailEffect {
    data object Deleted : BalanceUpdateDetailEffect
    data class ShowMessage(
        override val message: String,
    ) : BalanceUpdateDetailEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BalanceUpdateDetailViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val deleteBalanceUpdateRecordUseCase: DeleteBalanceUpdateRecordUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceUpdateDetailUiState())
    val uiState: StateFlow<BalanceUpdateDetailUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BalanceUpdateDetailEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                transactionRepository.observeChangeVersion().collect {
                    loadRecord()
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun delete() {
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            runCatching {
                deleteBalanceUpdateRecordUseCase(recordId)
            }.onSuccess {
                emitDeletedOnce()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isDeleting = false)
                effects.emit(BalanceUpdateDetailEffect.ShowMessage(throwable.message ?: "撤销失败"))
            }
        }
    }

    private suspend fun loadRecord() {
        val record = transactionRepository.getBalanceUpdateRecordById(recordId)
        if (record == null) {
            emitDeletedOnce()
            return
        }

        val account = accountRepository.getAccountById(record.accountId)

        _uiState.value = BalanceUpdateDetailUiState(
            recordId = record.id,
            accountId = record.accountId,
            accountName = account?.name ?: "未知账户",
            actualBalance = record.actualBalance,
            systemBalanceBeforeUpdate = record.systemBalanceBeforeUpdate,
            delta = record.delta,
            occurredAt = record.occurredAt,
            isLoading = false,
        )
    }

    private suspend fun emitDeletedOnce() {
        if (closed) return
        closed = true
        effects.emit(BalanceUpdateDetailEffect.Deleted)
    }
}
