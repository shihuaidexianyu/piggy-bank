package com.shihuaidexianyu.money.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.UiEffect
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
    val isSaving: Boolean = false,
    val fieldError: String? = null,
)

sealed interface SharePreviewEffect {
    data object Saved : SharePreviewEffect
    data class ShowMessage(override val message: String) : SharePreviewEffect, UiEffect.HasMessage
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
    val uiState: StateFlow<SharePreviewUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<SharePreviewEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        persist(_uiState.value)
        viewModelScope.launch {
            runCatching { accountLoader.loadOpenAccounts() }
                .onSuccess { accounts ->
                    val selected = _uiState.value.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id
                    updateState {
                        copy(
                            accounts = accounts,
                            selectedAccountId = selected,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { updateState { copy(isLoading = false, fieldError = "无法读取开放账户") } }
        }
    }

    fun updateDirection(value: CashFlowDirection) = updateState { copy(direction = value) }
    fun updateAmount(value: String) = updateState { copy(amountText = value, fieldError = null) }
    fun updateNote(value: String) = updateState {
        copy(
            note = value,
            fieldError = if (value.length > MAX_NOTE_LENGTH) "备注不能超过 200 字" else null,
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
        if (state.note.length > MAX_NOTE_LENGTH) return validationError("备注不能超过 200 字")
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
            }.onFailure { error ->
                saveInFlight = false
                updateState { copy(isSaving = false, fieldError = error.message ?: "保存失败") }
                effects.emit(SharePreviewEffect.ShowMessage(error.message ?: "保存失败"))
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
            note = savedStateHandle[NOTE_KEY] ?: originalText.trim().take(MAX_NOTE_LENGTH),
            occurredAt = occurredAt,
            selectedAccountId = savedStateHandle[ACCOUNT_ID_KEY],
            isUncertain = parsed.isUncertain,
            candidateAmounts = parsed.candidates.map { it.amountInMinor },
        )
    }

    private fun updateState(transform: SharePreviewUiState.() -> SharePreviewUiState) {
        _uiState.value = _uiState.value.transform()
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
        const val MAX_NOTE_LENGTH = 200
        const val OPERATION_ID_KEY = "share_preview_operation_id"
        const val ORIGINAL_TEXT_STATE_KEY = "share_preview_original_text"
        const val DIRECTION_KEY = "share_preview_direction"
        const val AMOUNT_KEY = "share_preview_amount"
        const val NOTE_KEY = "share_preview_note"
        const val OCCURRED_AT_KEY = "share_preview_occurred_at"
        const val ACCOUNT_ID_KEY = "share_preview_account_id"
    }
}
