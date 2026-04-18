package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
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
    val purpose: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val showPurposeConfirm: Boolean = false,
)

sealed interface RecordCashFlowEffect {
    data object Saved : RecordCashFlowEffect
    data class ShowMessage(override val message: String) : RecordCashFlowEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class RecordCashFlowViewModel(
    private val direction: CashFlowDirection,
    initialAccountId: Long?,
    prefillAmount: Long? = null,
    prefillPurpose: String? = null,
    private val reminderId: Long? = null,
    private val accountRepository: AccountRepository,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val confirmReminderUseCase: ConfirmReminderUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecordCashFlowUiState(
            direction = direction,
            selectedAccountId = initialAccountId,
            amountText = prefillAmount?.let {
                BigDecimal.valueOf(it, 2)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
            } ?: "",
            purpose = prefillPurpose ?: "",
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
                    accounts = accounts.toAccountOptionUiModels(),
                    selectedAccountId = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id,
                )
            } catch (e: Exception) {
                android.util.Log.e("RecordCashFlowViewModel", "Failed to load accounts", e)
            }
        }
    }

    fun updateAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value)
    }

    fun updatePurpose(value: String) {
        _uiState.value = _uiState.value.copy(purpose = value)
    }

    fun updateOccurredAt(value: Long) {
        _uiState.value = _uiState.value.copy(
            occurredAtMillis = DateTimeTextFormatter.floorToMinute(value),
        )
    }

    fun dismissPurposeConfirm() {
        _uiState.value = _uiState.value.copy(showPurposeConfirm = false)
    }

    fun save(confirmBlankPurpose: Boolean = false) {
        val state = _uiState.value
        if (state.purpose.isBlank() && !confirmBlankPurpose) {
            _uiState.value = state.copy(showPurposeConfirm = true)
            return
        }

        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.message!!)); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.message!!)); return@launch }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.message!!)); return@launch }

            _uiState.value = state.copy(isSaving = true, showPurposeConfirm = false)
            runCatching {
                createCashFlowRecordUseCase(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    purpose = state.purpose,
                    occurredAt = state.occurredAtMillis,
                )
            }.onSuccess {
                if (reminderId != null && confirmReminderUseCase != null) {
                    runCatching { confirmReminderUseCase(reminderId) }
                }
                effects.emit(RecordCashFlowEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(RecordCashFlowEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }
}

