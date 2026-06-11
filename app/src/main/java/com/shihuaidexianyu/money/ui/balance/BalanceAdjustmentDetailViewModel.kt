package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
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
)

sealed interface BalanceAdjustmentDetailEffect {
    data object Closed : BalanceAdjustmentDetailEffect
}

class BalanceAdjustmentDetailViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
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
}
