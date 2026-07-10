package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
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
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val amountText: String = "",
    val note: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val noteSuggestions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val showNoteConfirm: Boolean = false,
)

sealed interface RecordCashFlowEffect {
    data object Saved : RecordCashFlowEffect
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
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val processDueReminderUseCase: ProcessDueReminderUseCase? = null,
    private val savedStateHandle: SavedStateHandle,
    operationIdFactory: LedgerOperationIdFactory,
) : ViewModel() {
    private val operationId = savedOperationId(
        existing = savedStateHandle[OPERATION_ID_KEY],
        factory = operationIdFactory,
    ).also { savedStateHandle[OPERATION_ID_KEY] = it }
    private var saveInFlight = false
    private val _uiState = MutableStateFlow(
        RecordCashFlowUiState(
            direction = direction,
            selectedAccountId = initialAccountId,
            amountText = prefillAmount?.let {
                BigDecimal.valueOf(it, 2)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
            } ?: "",
            note = prefillNote ?: "",
        ),
    )
    val uiState: StateFlow<RecordCashFlowUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<RecordCashFlowEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryActiveAccounts()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = calculateCurrentBalanceUseCase(account.id),
                        )
                    },
                    selectedAccountId = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id,
                )
                refreshNoteSuggestions()
            } catch (e: Exception) {
                android.util.Log.e("RecordCashFlowViewModel", "Failed to load accounts", e)
            }
        }
    }

    fun updateAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        refreshNoteSuggestions()
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value)
    }

    fun updateNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value)
    }

    fun applyNoteSuggestion(value: String) {
        _uiState.value = _uiState.value.copy(note = value)
    }

    fun updateOccurredAt(value: Long) {
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
    }

    fun dismissNoteConfirm() {
        _uiState.value = _uiState.value.copy(showNoteConfirm = false)
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

    fun save(confirmBlankNote: Boolean = false) {
        val state = _uiState.value
        if (state.note.isBlank() && !confirmBlankNote) {
            _uiState.value = state.copy(showNoteConfirm = true)
            return
        }
        if (saveInFlight) return
        saveInFlight = true

        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error ->
                    saveInFlight = false
                    effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("请选择账户")))
                    return@launch
                }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error ->
                    saveInFlight = false
                    effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("请输入有效金额")))
                    return@launch
                }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error ->
                    saveInFlight = false
                    effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("时间不能晚于当前时间")))
                    return@launch
                }

            _uiState.value = state.copy(isSaving = true, showNoteConfirm = false)
            runCatching {
                if (reminderId != null && processDueReminderUseCase != null) {
                    processDueReminderUseCase(
                        reminderId = reminderId,
                        expectedDueAt = requireNotNull(expectedDueAt) { "提醒时间不存在" },
                        occurredAt = state.occurredAtMillis,
                        amount = amount,
                        note = state.note,
                    )
                } else {
                    createCashFlowRecordUseCase(
                        accountId = accountId,
                        direction = direction,
                        amount = amount,
                        note = state.note,
                        occurredAt = state.occurredAtMillis,
                        operationId = operationId,
                    )
                }
            }.onSuccess {
                effects.emit(RecordCashFlowEffect.Saved)
            }.onFailure { throwable ->
                saveInFlight = false
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(RecordCashFlowEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }

    private companion object {
        const val OPERATION_ID_KEY = "record_cash_flow_operation_id"
    }
}
