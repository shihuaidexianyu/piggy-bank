package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class UpdateBalanceUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val actualBalanceText: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val systemBalanceBeforeUpdate: Long = 0,
    val actualBalancePreview: Long? = null,
    val deltaPreview: Long? = null,
    val actualBalanceEdited: Boolean = false,
    val accountError: String? = null,
    val actualBalanceError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val latestResult: UpdateBalanceResult? = null,
    val pendingTerminal: PendingFormTerminal? = null,
)

sealed interface UpdateBalanceEffect {
    data class ShowMessage(
        override val message: String,
    ) : UpdateBalanceEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class UpdateBalanceViewModel(
    initialAccountId: Long?,
    private val accountRepository: AccountRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
    private val savedStateHandle: SavedStateHandle,
    operationIdFactory: LedgerOperationIdFactory,
) : ViewModel() {
    private val restoredDraft = savedStateHandle.get<BalanceFormDraft>(DRAFT_KEY)
    private val restoredTerminal = savedStateHandle.get<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
    private val restoredResult = savedStateHandle.get<UpdateBalanceResult>(LATEST_RESULT_KEY)
    private val operationId = savedOperationId(
        existing = restoredDraft?.operationId ?: savedStateHandle[OPERATION_ID_KEY],
        factory = operationIdFactory,
    ).also { savedStateHandle[OPERATION_ID_KEY] = it }
    private var saveInFlight = false
    private val _uiState = MutableStateFlow(
        restoredDraft?.let { draft ->
            UpdateBalanceUiState(
                selectedAccountId = draft.selectedAccountId,
                actualBalanceText = draft.actualBalanceText,
                occurredAtMillis = draft.occurredAtMillis,
                actualBalanceEdited = draft.actualBalanceEdited,
                accountError = draft.accountError,
                actualBalanceError = draft.actualBalanceError,
                occurredAtError = draft.occurredAtError,
                isDirty = draft.isDirty,
                latestResult = restoredResult ?: restoredTerminal?.balanceResult,
                pendingTerminal = restoredTerminal,
            )
        } ?: UpdateBalanceUiState(
            selectedAccountId = initialAccountId,
            latestResult = restoredResult ?: restoredTerminal?.balanceResult,
            pendingTerminal = restoredTerminal,
        ),
    )
    val uiState: StateFlow<UpdateBalanceUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<UpdateBalanceEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var previewJob: Job? = null

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
                val openAccountIds = accounts.mapTo(mutableSetOf()) { it.id }
                val selected = _uiState.value.selectedAccountId
                    ?.takeIf(openAccountIds::contains)
                    ?: accounts.firstOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = selected,
                    accountError = if (selected != null) null else _uiState.value.accountError,
                    isLoading = false,
                    loadErrorMessage = null,
                )
                refreshPreview(resetActualBalanceToSystem = !_uiState.value.actualBalanceEdited)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("UpdateBalanceViewModel", "Failed to load accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "开放账户加载失败，请重试",
                )
            }
        }
    }

    fun updateAccount(accountId: Long) {
        updateDraft {
            copy(
                selectedAccountId = accountId,
                accountError = null,
                actualBalanceEdited = false,
                isDirty = true,
            )
        }
        refreshPreview(resetActualBalanceToSystem = true)
    }

    fun updateActualBalance(value: String) {
        val actual = AmountInputParser.parseSignedToMinor(value)
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        updateDraft {
            copy(
                actualBalanceText = value,
                actualBalancePreview = actual,
                deltaPreview = actual?.minus(systemBalance),
                actualBalanceEdited = true,
                actualBalanceError = null,
                isDirty = true,
            )
        }
    }

    fun updateOccurredAt(value: Long) {
        val shouldResetActualBalance = !_uiState.value.actualBalanceEdited
        updateDraft {
            copy(
                occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
                occurredAtError = null,
                isDirty = true,
            )
        }
        refreshPreview(resetActualBalanceToSystem = shouldResetActualBalance)
    }

    fun resetActualBalanceToSystem() {
        val systemBalance = _uiState.value.systemBalanceBeforeUpdate
        updateDraft {
            copy(
                actualBalanceText = AmountFormatter.formatPlain(systemBalance),
                actualBalancePreview = systemBalance,
                deltaPreview = 0,
                actualBalanceEdited = false,
                actualBalanceError = null,
                isDirty = true,
            )
        }
    }

    fun save() {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        saveInFlight = true
        val state = _uiState.value
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(accountError = error.userMessage("请选择账户")) }
                    return@launch
                }
            val actualBalance = runCatching { RecordValidator.requireSignedAmount(state.actualBalanceText) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(actualBalanceError = error.userMessage("请输入有效金额")) }
                    return@launch
                }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error ->
                    saveInFlight = false
                    updateDraft { copy(occurredAtError = error.userMessage("时间不能晚于当前时间")) }
                    return@launch
                }

            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateBalanceUseCase(
                    accountId = accountId,
                    actualBalance = actualBalance,
                    occurredAt = state.occurredAtMillis,
                    operationId = operationId,
                )
            }.onSuccess { result ->
                savedStateHandle[LATEST_RESULT_KEY] = result
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    latestResult = result,
                    actualBalanceText = AmountFormatter.formatPlain(result.actualBalance),
                    systemBalanceBeforeUpdate = result.actualBalance,
                    actualBalancePreview = result.actualBalance,
                    deltaPreview = 0,
                    actualBalanceEdited = false,
                )
                setPendingTerminal(
                    pendingFormTerminal(
                        kind = FormTerminalKind.SAVED,
                        balanceResult = result,
                    ),
                )
            }.onFailure { throwable ->
                saveInFlight = false
                _uiState.value = _uiState.value.copy(isSaving = false)
                val message = throwable.message ?: "保存失败"
                when {
                    message.contains("时间不能") -> updateDraft { copy(occurredAtError = message) }
                    message.contains("账户") -> updateDraft { copy(accountError = message) }
                    else -> effects.emit(UpdateBalanceEffect.ShowMessage(message))
                }
            }
        }
    }

    fun refreshLedgerBalanceAfterSupplementalEntry() {
        refreshPreview(resetActualBalanceToSystem = false)
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
        const val OPERATION_ID_KEY = "update_balance_operation_id"
        const val DRAFT_KEY = "update_balance_draft"
        const val LATEST_RESULT_KEY = "update_balance_latest_result"
    }

    private fun updateDraft(transform: UpdateBalanceUiState.() -> UpdateBalanceUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next
        savedStateHandle[DRAFT_KEY] = BalanceFormDraft(
            selectedAccountId = next.selectedAccountId,
            actualBalanceText = next.actualBalanceText,
            occurredAtMillis = next.occurredAtMillis,
            actualBalanceEdited = next.actualBalanceEdited,
            accountError = next.accountError,
            actualBalanceError = next.actualBalanceError,
            occurredAtError = next.occurredAtError,
            isDirty = next.isDirty,
            operationId = operationId,
        )
    }

    private fun refreshPreview(resetActualBalanceToSystem: Boolean) {
        val snapshot = _uiState.value
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val systemBalance = snapshot.selectedAccountId
                ?.let { calculateCurrentBalanceUseCase(it, snapshot.occurredAtMillis) }
                ?: 0L
            val current = _uiState.value
            if (current.selectedAccountId != snapshot.selectedAccountId ||
                current.occurredAtMillis != snapshot.occurredAtMillis
            ) {
                return@launch
            }

            val actualBalanceText = if (resetActualBalanceToSystem) {
                AmountFormatter.formatPlain(systemBalance)
            } else {
                current.actualBalanceText
            }
            val actualBalancePreview = if (resetActualBalanceToSystem) {
                systemBalance
            } else {
                AmountInputParser.parseSignedToMinor(actualBalanceText)
            }

            _uiState.value = current.copy(
                actualBalanceText = actualBalanceText,
                systemBalanceBeforeUpdate = systemBalance,
                actualBalancePreview = actualBalancePreview,
                deltaPreview = actualBalancePreview?.minus(systemBalance),
                actualBalanceEdited = if (resetActualBalanceToSystem) false else current.actualBalanceEdited,
            )
        }
    }
}
