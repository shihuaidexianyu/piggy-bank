package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class EditBalanceUpdateUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val loadRetryToken: String? = null,
    val accountName: String = "",
    val actualBalanceText: String = "",
    val actualBalanceError: String? = null,
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val occurredAtError: String? = null,
    val systemBalanceBeforeUpdate: Long = 0,
    val actualBalancePreview: Long? = null,
    val deltaPreview: Long? = null,
    val isDirty: Boolean = false,
    val hasConflict: Boolean = false,
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface EditBalanceUpdateEffect {
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditBalanceUpdateUiState(
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<EditBalanceUpdateUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditBalanceUpdateEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    private var accountId: Long = 0
    private var closed = false
    private var expectedUpdatedAt: Long = 0L
    private var mutationInFlight = false
    private var previewJob: Job? = null

    init {
        loadRecord(useSavedDraft = true)
    }

    private fun loadRecord(useSavedDraft: Boolean) {
        viewModelScope.launch {
            try {
                val record = transactionRepository.getBalanceUpdateRecordById(recordId)
                if (record == null) {
                    emitDeletedOnce()
                    return@launch
                }
                val account = accountRepository.getAccountById(record.accountId)
                accountId = record.accountId
                val draft = savedStateHandle.get<EditBalanceUpdateFormDraft>(DRAFT_KEY)
                    .takeIf { useSavedDraft }
                expectedUpdatedAt = draft?.expectedUpdatedAt ?: record.updatedAt
                val occurredAt = draft?.occurredAtMillis ?: record.occurredAt
                val systemBalanceBeforeUpdate = if (occurredAt == record.occurredAt) {
                    record.systemBalanceBeforeUpdate
                } else {
                    resolveBalanceUpdateContextUseCase(
                        accountId = record.accountId,
                        occurredAt = occurredAt,
                        excludingRecordId = recordId,
                    ).systemBalanceBeforeUpdate
                }
                val actualBalanceText = draft?.actualBalanceText
                    ?: AmountFormatter.formatPlain(record.actualBalance)
                val actualBalance = AmountInputParser.parseSignedToMinor(actualBalanceText)
                _uiState.value = EditBalanceUpdateUiState(
                    isLoading = false,
                    accountName = account?.name ?: "未知账户",
                    actualBalanceText = actualBalanceText,
                    actualBalanceError = draft?.actualBalanceError,
                    occurredAtMillis = occurredAt,
                    occurredAtError = draft?.occurredAtError,
                    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
                    actualBalancePreview = actualBalance,
                    deltaPreview = actualBalance?.minus(systemBalanceBeforeUpdate),
                    isDirty = draft?.isDirty ?: false,
                    hasConflict = draft?.hasConflict ?: false,
                    pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditBalanceUpdateViewModel", "Failed to load record", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "核对记录加载失败，请重试",
                    loadRetryToken = "edit-balance:$recordId",
                )
            }
        }
    }

    fun retryLoad() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loadErrorMessage = null,
            loadRetryToken = null,
        )
        loadRecord(useSavedDraft = true)
    }

    fun updateActualBalance(value: String) {
        val actualBalance = AmountInputParser.parseSignedToMinor(value)
        updateDraft {
            copy(
                actualBalanceText = value,
                actualBalanceError = null,
                actualBalancePreview = actualBalance,
                deltaPreview = actualBalance?.minus(systemBalanceBeforeUpdate),
                isDirty = true,
            )
        }
    }

    fun updateOccurredAt(value: Long) {
        val occurredAt = DateTimeTextFormatter.floorToMinute(value)
        val actualBalanceText = _uiState.value.actualBalanceText
        updateDraft { copy(occurredAtMillis = occurredAt, occurredAtError = null, isDirty = true) }
        if (accountId <= 0) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val context = try {
                resolveBalanceUpdateContextUseCase(
                    accountId = accountId,
                    occurredAt = occurredAt,
                    excludingRecordId = recordId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.occurredAtMillis == occurredAt) {
                    val actualBalance = AmountInputParser.parseSignedToMinor(actualBalanceText)
                    updateDraft {
                        copy(
                            occurredAtError = error.userMessage("无法计算该时间的账面余额"),
                            actualBalancePreview = actualBalance,
                            deltaPreview = null,
                        )
                    }
                }
                return@launch
            }
            if (_uiState.value.occurredAtMillis != occurredAt) return@launch
            val actualBalance = AmountInputParser.parseSignedToMinor(actualBalanceText)
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
        if (mutationInFlight || _uiState.value.pendingTerminal != null) return
        mutationInFlight = true
        val state = _uiState.value
        viewModelScope.launch {
            val actualBalance = runCatching { RecordValidator.requireSignedAmount(state.actualBalanceText) }
                .getOrElse { error ->
                    mutationInFlight = false
                    updateDraft { copy(actualBalanceError = error.userMessage("请输入有效金额")) }
                    return@launch
                }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error ->
                    mutationInFlight = false
                    updateDraft { copy(occurredAtError = error.userMessage("时间不能晚于当前时间")) }
                    return@launch
                }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateBalanceUpdateRecordUseCase(
                    recordId = recordId,
                    actualBalance = actualBalance,
                    occurredAt = state.occurredAtMillis,
                    expectedUpdatedAt = expectedUpdatedAt,
                )
            }.onSuccess {
                setPendingTerminal(pendingFormTerminal(FormTerminalKind.SAVED))
            }.onFailure { throwable ->
                val lookup = try {
                    Result.success(transactionRepository.getBalanceUpdateRecordById(recordId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (lookupFailure: Exception) {
                    Result.failure(lookupFailure)
                }
                mutationInFlight = false
                if (lookup.isSuccess && lookup.getOrNull() == null) {
                    emitDeletedOnce()
                    return@onFailure
                }
                updateDraft { copy(isSaving = false, hasConflict = throwable is LedgerRecordChangedException) }
                val message = throwable.message ?: "保存失败"
                when {
                    throwable is LedgerRecordChangedException -> effects.emit(EditBalanceUpdateEffect.ShowMessage(message))
                    message.contains("时间不能") -> updateDraft { copy(occurredAtError = message) }
                    message.contains("账户") -> effects.emit(EditBalanceUpdateEffect.ShowMessage(message))
                    else -> effects.emit(EditBalanceUpdateEffect.ShowMessage(message))
                }
            }
        }
    }

    fun delete() {
        if (mutationInFlight || _uiState.value.pendingTerminal != null) return
        mutationInFlight = true
        _uiState.value = _uiState.value.copy(isSaving = true, showDeleteConfirm = false)
        viewModelScope.launch {
            runCatching {
                deleteBalanceUpdateRecordUseCase(recordId, expectedUpdatedAt)
            }.onSuccess { undoToken ->
                emitDeletedOnce(undoToken)
            }.onFailure { throwable ->
                mutationInFlight = false
                updateDraft {
                    copy(
                        isSaving = false,
                        showDeleteConfirm = false,
                        hasConflict = throwable is LedgerRecordChangedException,
                    )
                }
                effects.emit(EditBalanceUpdateEffect.ShowMessage(throwable.message ?: "撤销失败"))
            }
        }
    }

    private suspend fun emitDeletedOnce(undoToken: com.shihuaidexianyu.money.domain.model.LedgerUndoToken? = null) {
        if (closed) return
        closed = true
        if (_uiState.value.pendingTerminal == null) {
            setPendingTerminal(pendingFormTerminal(FormTerminalKind.DELETED, ledgerUndoToken = undoToken))
        }
    }

    fun ackTerminal(token: String) {
        if (_uiState.value.pendingTerminal?.token != token) return
        savedStateHandle.remove<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
        _uiState.value = _uiState.value.copy(pendingTerminal = null)
    }

    private fun setPendingTerminal(terminal: PendingFormTerminal) {
        savedStateHandle[PENDING_FORM_TERMINAL_KEY] = terminal
        _uiState.value = _uiState.value.copy(isSaving = false, pendingTerminal = terminal)
    }

    fun reload() {
        savedStateHandle.remove<EditBalanceUpdateFormDraft>(DRAFT_KEY)
        mutationInFlight = false
        _uiState.value = _uiState.value.copy(isLoading = true, isSaving = false, hasConflict = false)
        previewJob?.cancel()
        loadRecord(useSavedDraft = false)
    }

    private fun updateDraft(transform: EditBalanceUpdateUiState.() -> EditBalanceUpdateUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next
        savedStateHandle[DRAFT_KEY] = EditBalanceUpdateFormDraft(
            actualBalanceText = next.actualBalanceText,
            occurredAtMillis = next.occurredAtMillis,
            actualBalanceError = next.actualBalanceError,
            occurredAtError = next.occurredAtError,
            isDirty = next.isDirty,
            hasConflict = next.hasConflict,
            expectedUpdatedAt = expectedUpdatedAt,
        )
    }

    private companion object {
        const val DRAFT_KEY = "edit_balance_update_draft"
    }
}
