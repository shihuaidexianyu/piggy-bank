package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

data class BalanceAdjustmentDetailUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val accountName: String = "",
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val isDeleting: Boolean = false,
    val expectedUpdatedAt: Long = 0,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface BalanceAdjustmentDetailEffect {
    data class ShowMessage(
        override val message: String,
    ) : BalanceAdjustmentDetailEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BalanceAdjustmentDetailViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val deleteBalanceAdjustmentUseCase: DeleteBalanceAdjustmentUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceAdjustmentDetailUiState(pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY]))
    val uiState: StateFlow<BalanceAdjustmentDetailUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<BalanceAdjustmentDetailEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false
    private var deleteInFlight = false
    private var observationJob: Job? = null

    init {
        observeRecord()
    }

    fun retryLoad() {
        observeRecord()
    }

    private fun observeRecord() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
        observationJob = viewModelScope.launch {
            try {
                transactionRepository.observeChangeVersion().collect {
                    loadRecord()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("BalanceAdjustmentDetailViewModel", "Failed to observe record", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "调整记录加载失败，请重试",
                    isDeleting = false,
                )
            }
        }
    }

    fun delete() {
        if (closed || _uiState.value.isLoading || _uiState.value.loadErrorMessage != null) return
        if (deleteInFlight) return
        val expectedUpdatedAt = _uiState.value.expectedUpdatedAt
        deleteInFlight = true
        _uiState.value = _uiState.value.copy(isDeleting = true)
        viewModelScope.launch {
            try {
                val token = deleteBalanceAdjustmentUseCase(recordId, expectedUpdatedAt)
                emitClosedOnce(token)
            } catch (e: Exception) {
                deleteInFlight = false
                if (e !is com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException) {
                    runCatching {
                        android.util.Log.e("BalanceAdjustmentDetailViewModel", "Failed to delete adjustment", e)
                    }
                }
                _uiState.value = _uiState.value.copy(isDeleting = false)
                effects.emit(BalanceAdjustmentDetailEffect.ShowMessage(e.message ?: "删除失败"))
            }
        }
    }

    private suspend fun loadRecord() {
        val record = transactionRepository.getBalanceAdjustmentRecordById(recordId)
        if (record == null) {
            if (deleteInFlight) return
            emitClosedOnce()
            return
        }
        val account = accountRepository.getAccountById(record.accountId)
        _uiState.value = BalanceAdjustmentDetailUiState(
            isLoading = false,
            accountName = account?.name ?: "未知账户",
            delta = record.delta,
            occurredAt = record.occurredAt,
            expectedUpdatedAt = record.updatedAt,
            isDeleting = deleteInFlight,
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        )
    }

    private fun emitClosedOnce(token: com.shihuaidexianyu.money.domain.model.LedgerUndoToken? = null) {
        if (closed) return
        closed = true
        if (_uiState.value.pendingTerminal == null) {
            val terminal = pendingFormTerminal(FormTerminalKind.DELETED, ledgerUndoToken = token)
            savedStateHandle[PENDING_FORM_TERMINAL_KEY] = terminal
            _uiState.value = _uiState.value.copy(isDeleting = false, pendingTerminal = terminal)
        }
        deleteInFlight = false
    }

    fun ackTerminal(token: String) {
        if (_uiState.value.pendingTerminal?.token != token) return
        savedStateHandle.remove<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
        _uiState.value = _uiState.value.copy(pendingTerminal = null)
    }
}

