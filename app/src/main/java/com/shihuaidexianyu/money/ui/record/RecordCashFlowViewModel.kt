package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class RecordCashFlowUiState(
    val direction: CashFlowDirection,
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val noteSuggestions: List<String> = emptyList(),
    val noteError: String? = null,
    val accountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface RecordCashFlowEffect {
    data class ShowMessage(override val message: String) : RecordCashFlowEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class RecordCashFlowViewModel(
    private val direction: CashFlowDirection,
    initialAccountId: Long?,
    prefillAmount: Long? = null,
    prefillNote: String? = null,
    private val reminderId: Long? = null,
    private val expectedDueAt: Long? = null,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val processDueReminderUseCase: ProcessDueReminderUseCase? = null,
    private val savedStateHandle: SavedStateHandle,
    operationIdFactory: LedgerOperationIdFactory,
    private val devicePreferencesRepository: DevicePreferencesRepository? = null,
) : ViewModel() {
    private val restoredDraft = savedStateHandle.get<CashFlowFormDraft>(DRAFT_KEY)
    private val operationId = savedOperationId(
        existing = restoredDraft?.operationId ?: savedStateHandle[OPERATION_ID_KEY],
        factory = operationIdFactory,
    ).also { savedStateHandle[OPERATION_ID_KEY] = it }
    private var saveInFlight = false
    private val _uiState = MutableStateFlow(
        restoredDraft?.let { draft ->
            RecordCashFlowUiState(
                direction = direction,
                selectedAccountId = draft.selectedAccountId,
                amountText = draft.amountText,
                note = draft.note,
                occurredAtMillis = draft.occurredAtMillis,
                noteError = draft.noteError,
                accountError = draft.accountError,
                amountError = draft.amountError,
                occurredAtError = draft.occurredAtError,
                isDirty = draft.isDirty,
                pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
            )
        } ?: RecordCashFlowUiState(
            direction = direction,
            selectedAccountId = initialAccountId,
            amountText = prefillAmount?.let {
                BigDecimal.valueOf(it, 2)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
            } ?: "",
            note = prefillNote ?: "",
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<RecordCashFlowUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<RecordCashFlowEffect>(extraBufferCapacity = 1)
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
                val balances = calculateAccountBalancesUseCase(accounts)
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = balances.getValue(account.id),
                        )
                    },
                    selectedAccountId = defaultCashAccountId(
                        accounts = accounts,
                        recentAccountIds = recentAccountIds,
                        explicitAccountId = _uiState.value.selectedAccountId,
                    ),
                    isLoading = false,
                    loadErrorMessage = null,
                )
                refreshNoteSuggestions()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("RecordCashFlowViewModel", "Failed to load accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "开放账户加载失败，请重试",
                )
            }
        }
    }

    fun updateAccount(accountId: Long) {
        updateDraft { copy(selectedAccountId = accountId, accountError = null, isDirty = true) }
        refreshNoteSuggestions()
    }

    fun updateAmount(value: String) {
        updateDraft { copy(amountText = value, amountError = null, isDirty = true) }
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
        val accountId = _uiState.value.selectedAccountId
        viewModelScope.launch {
            runCatching {
                transactionRepository.queryRecentCashFlowNotes(
                    direction = direction.value,
                    accountId = accountId,
                    limit = 6,
                ).ifEmpty {
                    transactionRepository.queryRecentCashFlowNotes(
                        direction = direction.value,
                        accountId = null,
                        limit = 6,
                    )
                }
            }.onSuccess { suggestions ->
                _uiState.value = _uiState.value.copy(noteSuggestions = suggestions)
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (saveInFlight || state.pendingTerminal != null) return
        saveInFlight = true

        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(accountError = error.userMessage("请选择账户")) }
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
                if (reminderId != null && processDueReminderUseCase != null) {
                    processDueReminderUseCase(
                        reminderId = reminderId,
                        expectedDueAt = requireNotNull(expectedDueAt) { "提醒时间不存在" },
                        accountId = accountId,
                        direction = direction,
                        occurredAt = state.occurredAtMillis,
                        amount = amount,
                        note = note,
                    )
                } else {
                    createCashFlowRecordUseCase(
                        accountId = accountId,
                        direction = direction,
                        amount = amount,
                        note = note,
                        occurredAt = state.occurredAtMillis,
                        operationId = operationId,
                    )
                }
            }.onSuccess {
                rememberRecentAccounts(accountId)
                setPendingTerminal(pendingFormTerminal(FormTerminalKind.SAVED))
            }.onFailure { throwable ->
                saveInFlight = false
                _uiState.value = _uiState.value.copy(isSaving = false)
                val message = throwable.message ?: "保存失败"
                when {
                    message.contains("时间不能") -> updateDraft { copy(occurredAtError = message) }
                    message.contains("账户") -> updateDraft { copy(accountError = message) }
                    else -> effects.emit(RecordCashFlowEffect.ShowMessage(message))
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

    private fun updateDraft(transform: RecordCashFlowUiState.() -> RecordCashFlowUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next
        savedStateHandle[DRAFT_KEY] = CashFlowFormDraft(
            selectedAccountId = next.selectedAccountId,
            amountText = next.amountText,
            note = next.note,
            occurredAtMillis = next.occurredAtMillis,
            noteError = next.noteError,
            accountError = next.accountError,
            amountError = next.amountError,
            occurredAtError = next.occurredAtError,
            isDirty = next.isDirty,
            operationId = operationId,
        )
    }

    private companion object {
        const val OPERATION_ID_KEY = "record_cash_flow_operation_id"
        const val DRAFT_KEY = "record_cash_flow_draft"
    }
}
