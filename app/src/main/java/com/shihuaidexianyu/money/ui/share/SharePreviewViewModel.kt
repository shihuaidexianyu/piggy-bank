package com.shihuaidexianyu.money.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.record.MAX_LEDGER_NOTE_LENGTH
import com.shihuaidexianyu.money.ui.record.ledgerNoteLengthError
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.RecordValidator
import com.shihuaidexianyu.money.util.SharedTextAmountExtractor
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShareCashFlowPayload(
    val accountId: Long,
    val direction: CashFlowDirection,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val operationId: String,
)

fun interface SharePreviewAccountLoader {
    suspend fun loadOpenAccounts(): List<AccountOptionUiModel>
}

fun interface SharePreviewSubmitter {
    suspend fun submit(payload: ShareCashFlowPayload)
}

data class SharePreviewUiState(
    val originalText: String,
    val direction: CashFlowDirection,
    val amountText: String,
    val note: String,
    val occurredAt: Long,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val isUncertain: Boolean = true,
    val candidateAmounts: List<Long> = emptyList(),
    val isLoading: Boolean = true,
    val loadErrorMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val fieldError: String? = null,
    val isDirty: Boolean = false,
)

sealed interface SharePreviewEffect {
    data object Saved : SharePreviewEffect
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : SharePreviewEffect, UiEffect.HasMessage
}

class SharePreviewViewModel(
    originalText: String,
    private val savedStateHandle: SavedStateHandle,
    private val accountLoader: SharePreviewAccountLoader,
    private val submitter: SharePreviewSubmitter,
    operationIdFactory: LedgerOperationIdFactory,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private val operationId = savedOperationId(
        existing = savedStateHandle[OPERATION_ID_KEY],
        factory = operationIdFactory,
    ).also { savedStateHandle[OPERATION_ID_KEY] = it }
    private var saveInFlight = false
    private val parsed = SharedTextAmountExtractor.parse(originalText)
    private val _uiState = MutableStateFlow(restoreOrCreate(originalText))
    private var baseline = _uiState.value
    val uiState: StateFlow<SharePreviewUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<SharePreviewEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        persist(_uiState.value)
        loadAccounts()
    }

    fun retryLoad() {
        loadAccounts()
    }

    private fun loadAccounts() {
        updateState { copy(isLoading = true, loadErrorMessageRes = null) }
        viewModelScope.launch {
            runCatching { accountLoader.loadOpenAccounts() }
                .onSuccess { accounts ->
                    val selected = _uiState.value.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id
                    // The loaded (auto-selected) account is the clean baseline, not an edit.
                    baseline = baseline.copy(selectedAccountId = selected)
                    updateState {
                        copy(
                            accounts = accounts,
                            selectedAccountId = selected,
                            isLoading = false,
                            loadErrorMessageRes = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    updateState {
                        copy(
                            isLoading = false,
                            loadErrorMessageRes = R.string.share_accounts_load_failed,
                        )
                    }
                }
        }
    }

    fun updateDirection(value: CashFlowDirection) = updateState { copy(direction = value) }
    fun updateAmount(value: String) = updateState { copy(amountText = value, fieldError = null) }
    fun updateNote(value: String) = updateState {
        copy(
            note = value,
            fieldError = ledgerNoteLengthError(value),
        )
    }
    fun updateAccount(value: Long) = updateState { copy(selectedAccountId = value, fieldError = null) }
    fun updateOccurredAt(value: Long) = updateState {
        copy(occurredAt = DateTimeTextFormatter.floorToMinute(value), fieldError = null)
    }

    fun save() {
        if (saveInFlight) return
        val state = _uiState.value
        val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
            .getOrElse { return validationError(it.message ?: "请选择账户") }
        if (state.accounts.none { it.id == accountId }) return validationError("请选择开放账户")
        val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
            .getOrElse { return validationError(it.message ?: "请输入有效金额") }
        ledgerNoteLengthError(state.note)?.let { return validationError(it) }
        if (state.occurredAt > clockProvider.nowMillis()) return validationError("时间不能晚于当前时间")

        saveInFlight = true
        updateState { copy(isSaving = true, fieldError = null) }
        viewModelScope.launch {
            runCatching {
                submitter.submit(
                    ShareCashFlowPayload(
                        accountId = accountId,
                        direction = state.direction,
                        amount = amount,
                        note = state.note,
                        occurredAt = state.occurredAt,
                        operationId = operationId,
                    ),
                )
            }.onSuccess {
                saveInFlight = false
                updateState { copy(isSaving = false) }
                effects.emit(SharePreviewEffect.Saved)
            }.onFailure {
                saveInFlight = false
                updateState { copy(isSaving = false) }
                effects.emit(
                    SharePreviewEffect.ShowMessage("", messageRes = R.string.share_save_failed_retry),
                )
            }
        }
    }

    private fun validationError(message: String) {
        updateState { copy(fieldError = message) }
    }

    private fun restoreOrCreate(originalText: String): SharePreviewUiState {
        val occurredAt = savedStateHandle.get<Long>(OCCURRED_AT_KEY)
            ?: DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis())
        return SharePreviewUiState(
            originalText = savedStateHandle[ORIGINAL_TEXT_STATE_KEY] ?: originalText,
            direction = savedStateHandle.get<String>(DIRECTION_KEY)
                ?.let(CashFlowDirection::fromValue)
                ?: parsed.direction
                ?: CashFlowDirection.OUTFLOW,
            amountText = savedStateHandle[AMOUNT_KEY]
                ?: parsed.amountInMinor?.toEditableAmount().orEmpty(),
            note = savedStateHandle[NOTE_KEY] ?: originalText.trim().take(MAX_LEDGER_NOTE_LENGTH),
            occurredAt = occurredAt,
            selectedAccountId = savedStateHandle[ACCOUNT_ID_KEY],
            isUncertain = parsed.isUncertain,
            candidateAmounts = parsed.candidates.map { it.amountInMinor },
        )
    }

    private fun updateState(transform: SharePreviewUiState.() -> SharePreviewUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next.copy(
            isDirty = next.direction != baseline.direction ||
                next.amountText != baseline.amountText ||
                next.note != baseline.note ||
                next.occurredAt != baseline.occurredAt ||
                next.selectedAccountId != baseline.selectedAccountId,
        )
        persist(_uiState.value)
    }

    private fun persist(state: SharePreviewUiState) {
        savedStateHandle[ORIGINAL_TEXT_STATE_KEY] = state.originalText
        savedStateHandle[DIRECTION_KEY] = state.direction.value
        savedStateHandle[AMOUNT_KEY] = state.amountText
        savedStateHandle[NOTE_KEY] = state.note
        savedStateHandle[OCCURRED_AT_KEY] = state.occurredAt
        savedStateHandle[ACCOUNT_ID_KEY] = state.selectedAccountId
    }

    private fun Long.toEditableAmount(): String = BigDecimal.valueOf(this, 2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()

    companion object {
        const val OPERATION_ID_KEY = "share_preview_operation_id"
        const val ORIGINAL_TEXT_STATE_KEY = "share_preview_original_text"
        const val DIRECTION_KEY = "share_preview_direction"
        const val AMOUNT_KEY = "share_preview_amount"
        const val NOTE_KEY = "share_preview_note"
        const val OCCURRED_AT_KEY = "share_preview_occurred_at"
        const val ACCOUNT_ID_KEY = "share_preview_account_id"
    }
}
