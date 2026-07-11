package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class EditTransferUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val loadRetryToken: String? = null,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val noteTouched: Boolean = false,
    val noteError: String? = null,
    val fromAccountError: String? = null,
    val toAccountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean = false,
    val hasConflict: Boolean = false,
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val allFromAccountAmount: Long = 0L,
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface EditTransferEffect {
    data class ShowMessage(
        override val message: String,
    ) : EditTransferEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditTransferViewModel(
    private val recordId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val updateTransferRecordUseCase: UpdateTransferRecordUseCase,
    private val deleteTransferRecordUseCase: DeleteTransferRecordUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditTransferUiState(
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<EditTransferUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditTransferEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false
    private var originalFromAccountId: Long? = null
    private var originalToAccountId: Long? = null
    private var originalAmount: Long = 0L
    private var expectedUpdatedAt: Long = 0L
    private var mutationInFlight = false

    init {
        loadRecord(useSavedDraft = true)
    }

    private fun loadRecord(useSavedDraft: Boolean) {
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
                val draft = savedStateHandle.get<EditTransferFormDraft>(DRAFT_KEY)
                    .takeIf { useSavedDraft }
                expectedUpdatedAt = draft?.expectedUpdatedAt ?: record.updatedAt
                val accounts = accountRepository.queryOpenAccounts()
                val balances = calculateAccountBalancesUseCase(accounts)
                val nextState = EditTransferUiState(
                    isLoading = false,
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = balances.getValue(account.id),
                        )
                    },
                    fromAccountId = draft?.fromAccountId ?: record.fromAccountId,
                    toAccountId = draft?.toAccountId ?: record.toAccountId,
                    amountText = draft?.amountText ?: AmountFormatter.formatPlain(record.amount),
                    note = draft?.note ?: record.note,
                    noteTouched = draft?.noteTouched ?: false,
                    noteError = draft?.noteError,
                    fromAccountError = draft?.fromAccountError,
                    toAccountError = draft?.toAccountError,
                    amountError = draft?.amountError,
                    occurredAtError = draft?.occurredAtError,
                    occurredAtMillis = draft?.occurredAtMillis ?: record.occurredAt,
                    isDirty = draft?.isDirty ?: false,
                    hasConflict = draft?.hasConflict ?: false,
                    pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
                )
                _uiState.value = nextState.withAllFromAccountAmount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditTransferViewModel", "Failed to load record", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "记录加载失败，请重试",
                    loadRetryToken = "edit-transfer:$recordId",
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

    fun updateFromAccount(accountId: Long) = updateDraft {
        copy(fromAccountId = accountId, fromAccountError = null, toAccountError = null, isDirty = true)
    }
    fun updateToAccount(accountId: Long) = updateDraft {
        copy(toAccountId = accountId, fromAccountError = null, toAccountError = null, isDirty = true)
    }
    fun swapAccounts() = updateDraft {
        copy(
            fromAccountId = toAccountId,
            toAccountId = fromAccountId,
            fromAccountError = null,
            toAccountError = null,
            isDirty = true,
        )
    }
    fun updateAmount(value: String) = updateDraft { copy(amountText = value, amountError = null, isDirty = true) }
    fun useAllFromAccountBalance() = updateDraft {
        if (allFromAccountAmount > 0L) {
            copy(amountText = AmountFormatter.formatPlain(allFromAccountAmount), amountError = null, isDirty = true)
        } else {
            this
        }
    }
    fun updateNote(value: String) = updateDraft {
        copy(
            note = value,
            noteTouched = true,
            noteError = if (value.trim().length > MAX_LEDGER_NOTE_LENGTH) {
                "备注不能超过 $MAX_LEDGER_NOTE_LENGTH 个字符"
            } else {
                null
            },
            isDirty = true,
        )
    }
    fun updateOccurredAt(value: Long) = updateDraft {
        copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
            occurredAtError = null,
            isDirty = true,
        )
    }
    fun showDeleteConfirm() = updateState { copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = updateState { copy(showDeleteConfirm = false) }

    fun save() {
        if (mutationInFlight || _uiState.value.pendingTerminal != null) return
        mutationInFlight = true
        val state = _uiState.value
        viewModelScope.launch {
            val (fromId, toId) = runCatching { RecordValidator.requireTransferAccounts(state.fromAccountId, state.toAccountId) }
                .getOrElse { error ->
                    mutationInFlight = false
                    val message = error.userMessage("请选择账户")
                    updateDraft {
                        when {
                            state.fromAccountId == null -> copy(fromAccountError = message)
                            state.toAccountId == null -> copy(toAccountError = message)
                            else -> copy(fromAccountError = message, toAccountError = message)
                        }
                    }
                    return@launch
                }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error ->
                    mutationInFlight = false
                    updateDraft { copy(amountError = error.userMessage("请输入有效金额")) }
                    return@launch
                }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error ->
                    mutationInFlight = false
                    updateDraft { copy(occurredAtError = error.userMessage("时间不能晚于当前时间")) }
                    return@launch
                }
            val note = if (state.noteTouched) {
                runCatching { normalizeLedgerNote(state.note) }
                    .getOrElse { error ->
                        mutationInFlight = false
                        updateDraft { copy(noteError = error.message) }
                        return@launch
                    }
            } else {
                state.note
            }
            updateState { copy(isSaving = true, note = note, noteError = null) }
            runCatching {
                updateTransferRecordUseCase(
                    recordId = recordId,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = note,
                    occurredAt = state.occurredAtMillis,
                    preserveNoteVerbatim = !state.noteTouched,
                    expectedUpdatedAt = expectedUpdatedAt,
                )
            }.onSuccess { setPendingTerminal(pendingFormTerminal(FormTerminalKind.SAVED)) }
                .onFailure { failure ->
                    val lookup = try {
                        Result.success(transactionRepository.queryTransferRecordById(recordId))
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
                    updateDraft {
                        copy(
                            isSaving = false,
                            hasConflict = failure is LedgerRecordChangedException,
                        )
                    }
                    val message = failure.message ?: "保存失败"
                    when {
                        failure is LedgerRecordChangedException -> effects.emit(EditTransferEffect.ShowMessage(message))
                        message.contains("时间不能") -> updateDraft { copy(occurredAtError = message) }
                        message.contains("转出账户") -> updateDraft { copy(fromAccountError = message) }
                        message.contains("转入账户") -> updateDraft { copy(toAccountError = message) }
                        message.contains("账户") -> updateDraft {
                            copy(fromAccountError = message, toAccountError = message)
                        }
                        else -> effects.emit(EditTransferEffect.ShowMessage(message))
                    }
                }
        }
    }

    fun delete() {
        if (mutationInFlight || _uiState.value.pendingTerminal != null) return
        mutationInFlight = true
        updateState { copy(isSaving = true, showDeleteConfirm = false) }
        viewModelScope.launch {
            runCatching { deleteTransferRecordUseCase(recordId, expectedUpdatedAt) }
                .onSuccess { undoToken ->
                    emitDeletedOnce(undoToken)
                }
                .onFailure {
                    mutationInFlight = false
                    updateDraft {
                        copy(
                            isSaving = false,
                            showDeleteConfirm = false,
                            hasConflict = it is LedgerRecordChangedException,
                        )
                    }
                    effects.emit(EditTransferEffect.ShowMessage(it.message ?: "删除失败"))
                }
        }
    }

    private fun updateState(transform: EditTransferUiState.() -> EditTransferUiState) {
        _uiState.value = _uiState.value.transform().withAllFromAccountAmount()
    }

    fun reload() {
        savedStateHandle.remove<EditTransferFormDraft>(DRAFT_KEY)
        mutationInFlight = false
        _uiState.value = _uiState.value.copy(isLoading = true, isSaving = false, hasConflict = false)
        loadRecord(useSavedDraft = false)
    }

    private fun updateDraft(transform: EditTransferUiState.() -> EditTransferUiState) {
        val next = _uiState.value.transform().withAllFromAccountAmount()
        _uiState.value = next
        savedStateHandle[DRAFT_KEY] = EditTransferFormDraft(
            fromAccountId = next.fromAccountId,
            toAccountId = next.toAccountId,
            amountText = next.amountText,
            note = next.note,
            occurredAtMillis = next.occurredAtMillis,
            noteTouched = next.noteTouched,
            noteError = next.noteError,
            fromAccountError = next.fromAccountError,
            toAccountError = next.toAccountError,
            amountError = next.amountError,
            occurredAtError = next.occurredAtError,
            isDirty = next.isDirty,
            hasConflict = next.hasConflict,
            expectedUpdatedAt = expectedUpdatedAt,
        )
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

    private companion object {
        const val DRAFT_KEY = "edit_transfer_draft"
    }
}
