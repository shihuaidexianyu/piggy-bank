package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
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

data class BalanceUpdateDetailUiState(
    val recordId: Long = 0,
    val accountId: Long = 0,
    val accountName: String = "",
    val actualBalance: Long = 0,
    val systemBalanceBeforeUpdate: Long = 0,
    val delta: Long = 0,
    val occurredAt: Long = 0,
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val isDeleting: Boolean = false,
    val expectedUpdatedAt: Long = 0,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface BalanceUpdateDetailEffect {
    data class ShowMessage(
        override val message: String,
    ) : BalanceUpdateDetailEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BalanceUpdateDetailViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val deleteBalanceUpdateRecordUseCase: DeleteBalanceUpdateRecordUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(BalanceUpdateDetailUiState(pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY]))
    val uiState: StateFlow<BalanceUpdateDetailUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BalanceUpdateDetailEffect>(extraBufferCapacity = 1)
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
                runCatching { android.util.Log.e("BalanceUpdateDetailViewModel", "Failed to observe record", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "核对记录加载失败，请重试",
                    isDeleting = false,
                )
            }
        }
    }

    fun delete() {
        if (
            closed ||
            deleteInFlight ||
            _uiState.value.pendingTerminal != null ||
            _uiState.value.isLoading ||
            _uiState.value.loadErrorMessage != null
        ) return
        val expectedUpdatedAt = _uiState.value.expectedUpdatedAt
        deleteInFlight = true
        _uiState.value = _uiState.value.copy(isDeleting = true)
        viewModelScope.launch {
            runCatching {
                deleteBalanceUpdateRecordUseCase(recordId, expectedUpdatedAt)
            }.onSuccess { token ->
                emitDeletedOnce(token)
            }.onFailure { throwable ->
                deleteInFlight = false
                _uiState.value = _uiState.value.copy(isDeleting = false)
                effects.emit(BalanceUpdateDetailEffect.ShowMessage(throwable.message ?: "删除失败"))
            }
        }
    }

    private suspend fun loadRecord() {
        val record = transactionRepository.getBalanceUpdateRecordById(recordId)
        if (record == null) {
            if (deleteInFlight) return
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
            expectedUpdatedAt = record.updatedAt,
            isLoading = false,
            isDeleting = deleteInFlight,
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        )
    }

    private fun emitDeletedOnce(token: com.shihuaidexianyu.money.domain.model.LedgerUndoToken? = null) {
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
