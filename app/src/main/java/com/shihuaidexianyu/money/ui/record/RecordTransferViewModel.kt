package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
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
import kotlinx.coroutines.launch

data class RecordTransferUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val noteSuggestions: List<String> = emptyList(),
    val noteError: String? = null,
    val fromAccountError: String? = null,
    val toAccountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface RecordTransferEffect {
    data class ShowMessage(
        override val message: String,
    ) : RecordTransferEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class RecordTransferViewModel(
    initialFromAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val createTransferRecordUseCase: CreateTransferRecordUseCase,
    private val savedStateHandle: SavedStateHandle,
    operationIdFactory: LedgerOperationIdFactory,
    private val devicePreferencesRepository: DevicePreferencesRepository? = null,
) : ViewModel() {
    private val restoredDraft = savedStateHandle.get<TransferFormDraft>(DRAFT_KEY)
    private val operationId = savedOperationId(
        existing = restoredDraft?.operationId ?: savedStateHandle[OPERATION_ID_KEY],
        factory = operationIdFactory,
    ).also { savedStateHandle[OPERATION_ID_KEY] = it }
    private var saveInFlight = false
    private val _uiState = MutableStateFlow(
        restoredDraft?.let { draft ->
            RecordTransferUiState(
                fromAccountId = draft.fromAccountId,
                toAccountId = draft.toAccountId,
                amountText = draft.amountText,
                note = draft.note,
                occurredAtMillis = draft.occurredAtMillis,
                noteError = draft.noteError,
                fromAccountError = draft.fromAccountError,
                toAccountError = draft.toAccountError,
                amountError = draft.amountError,
                occurredAtError = draft.occurredAtError,
                isDirty = draft.isDirty,
                pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
            )
        } ?: RecordTransferUiState(
            fromAccountId = initialFromAccountId,
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<RecordTransferUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<RecordTransferEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        loadDependencies()
    }

    fun retryLoad() {
        loadDependencies()
    }

    private fun loadDependencies() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryOpenAccounts()
                val recentAccountIds = runCatching {
                    devicePreferencesRepository?.query()?.recentAccountIds.orEmpty()
                }.getOrDefault(emptyList())
                val selection = defaultTransferAccountIds(
                    accounts = accounts,
                    recentAccountIds = recentAccountIds,
                    explicitFromAccountId = _uiState.value.fromAccountId,
                )
                val balances = calculateAccountBalancesUseCase(accounts)
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = balances.getValue(account.id),
                        )
                    },
                    fromAccountId = _uiState.value.fromAccountId ?: selection.fromAccountId,
                    toAccountId = _uiState.value.toAccountId ?: selection.toAccountId,
                    isLoading = false,
                    loadErrorMessage = null,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("RecordTransferViewModel", "Failed to load accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "开放账户加载失败，请重试",
                )
            }
        }
    }

    fun updateFromAccount(accountId: Long) {
        updateDraft { copy(fromAccountId = accountId, fromAccountError = null, isDirty = true) }
        refreshNoteSuggestions()
    }

    fun updateToAccount(accountId: Long) {
        updateDraft { copy(toAccountId = accountId, toAccountError = null, isDirty = true) }
        refreshNoteSuggestions()
    }

    fun swapAccounts() {
        val state = _uiState.value
        updateDraft {
            copy(
                fromAccountId = toAccountId,
                toAccountId = fromAccountId,
                fromAccountError = null,
                toAccountError = null,
                isDirty = true,
            )
        }
        refreshNoteSuggestions()
    }

    fun updateAmount(value: String) {
        updateDraft { copy(amountText = value, amountError = null, isDirty = true) }
    }

    fun useAllFromAccountBalance() {
        val state = _uiState.value
        val balance = state.accounts.firstOrNull { it.id == state.fromAccountId }?.balance ?: return
        if (balance > 0L) {
            updateDraft {
                copy(
                    amountText = AmountFormatter.formatPlain(balance),
                    amountError = null,
                    isDirty = true,
                )
            }
        }
    }

    fun updateNote(value: String) {
        updateDraft {
            copy(
                note = value,
                noteError = if (value.trim().length > MAX_LEDGER_NOTE_LENGTH) {
                    "备注不能超过 $MAX_LEDGER_NOTE_LENGTH 个字符"
                } else {
                    null
                },
                isDirty = true,
            )
        }
    }

    fun applyNoteSuggestion(value: String) {
        updateNote(value)
    }

    fun updateOccurredAt(value: Long) {
        updateDraft {
            copy(
                occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
                occurredAtError = null,
                isDirty = true,
            )
        }
    }

    private fun refreshNoteSuggestions() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                transactionRepository.queryRecentTransferNotes(
                    fromAccountId = state.fromAccountId,
                    toAccountId = state.toAccountId,
                    limit = 6,
                ).ifEmpty {
                    transactionRepository.queryRecentTransferNotes(
                        fromAccountId = null,
                        toAccountId = null,
                        limit = 6,
                    )
                }
            }.onSuccess { suggestions ->
                _uiState.value = _uiState.value.copy(noteSuggestions = suggestions)
            }
        }
    }

    fun save() {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        saveInFlight = true
        val state = _uiState.value
        viewModelScope.launch {
            val fromId = state.fromAccountId ?: run {
                saveInFlight = false
                updateDraft { copy(fromAccountError = "请选择转出账户") }
                return@launch
            }
            val toId = state.toAccountId ?: run {
                saveInFlight = false
                updateDraft { copy(toAccountError = "请选择转入账户") }
                return@launch
            }
            if (fromId == toId) {
                saveInFlight = false
                updateDraft {
                    copy(
                        fromAccountError = "请选择不同的转出和转入账户",
                        toAccountError = "请选择不同的转出和转入账户",
                    )
                }
                return@launch
            }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(amountError = error.userMessage("请输入有效金额")) }
                    return@launch
                }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(occurredAtError = error.userMessage("时间不能晚于当前时间")) }
                    return@launch
                }
            val note = runCatching { normalizeLedgerNote(state.note) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(noteError = error.message) }
                    return@launch
                }

            _uiState.value = state.copy(isSaving = true, note = note, noteError = null)
            runCatching {
                createTransferRecordUseCase(
                    fromAccountId = fromId,
                    toAccountId = toId,
                    amount = amount,
                    note = note,
                    occurredAt = state.occurredAtMillis,
                    operationId = operationId,
                )
            }.onSuccess {
                rememberRecentAccounts(fromId, toId)
                setPendingTerminal(pendingFormTerminal(FormTerminalKind.SAVED))
            }.onFailure { throwable ->
                saveInFlight = false
                _uiState.value = _uiState.value.copy(isSaving = false)
                val message = throwable.message ?: "保存失败"
                when {
                    message.contains("时间不能") -> updateDraft { copy(occurredAtError = message) }
                    message.contains("转出账户") -> updateDraft { copy(fromAccountError = message) }
                    message.contains("转入账户") -> updateDraft { copy(toAccountError = message) }
                    message.contains("账户") -> updateDraft {
                        copy(fromAccountError = message, toAccountError = message)
                    }
                    else -> effects.emit(RecordTransferEffect.ShowMessage(message))
                }
            }
        }
    }

    private suspend fun rememberRecentAccounts(vararg accountIds: Long) {
        val repository = devicePreferencesRepository ?: return
        runCatching {
            val existing = repository.query().recentAccountIds
            repository.updateRecentAccountIds(accountIds.toList() + existing)
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

    private fun updateDraft(transform: RecordTransferUiState.() -> RecordTransferUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next
        savedStateHandle[DRAFT_KEY] = TransferFormDraft(
            fromAccountId = next.fromAccountId,
            toAccountId = next.toAccountId,
            amountText = next.amountText,
            note = next.note,
            occurredAtMillis = next.occurredAtMillis,
            noteError = next.noteError,
            fromAccountError = next.fromAccountError,
            toAccountError = next.toAccountError,
            amountError = next.amountError,
            occurredAtError = next.occurredAtError,
            isDirty = next.isDirty,
            operationId = operationId,
        )
    }

    private companion object {
        const val OPERATION_ID_KEY = "record_transfer_operation_id"
        const val DRAFT_KEY = "record_transfer_draft"
    }
}
