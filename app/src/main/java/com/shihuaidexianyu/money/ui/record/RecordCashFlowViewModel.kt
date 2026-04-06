package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.util.AmountInputParser
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
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
                java.math.BigDecimal.valueOf(it, 2)
                    .setScale(2, java.math.RoundingMode.DOWN)
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
            } catch (_: Exception) {
                // leave current state as-is
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
            val accountId = state.selectedAccountId
            if (accountId == null) {
                effects.emit(RecordCashFlowEffect.ShowMessage("请选择账户"))
                return@launch
            }

            val amount = AmountInputParser.parseToMinor(state.amountText)
            if (amount == null) {
                effects.emit(RecordCashFlowEffect.ShowMessage("金额不能为空"))
                return@launch
            }
            if (amount <= 0) {
                effects.emit(RecordCashFlowEffect.ShowMessage("金额必须大于 0"))
                return@launch
            }

            val occurredAt = state.occurredAtMillis
            if (occurredAt > System.currentTimeMillis()) {
                effects.emit(RecordCashFlowEffect.ShowMessage("时间不能晚于当前时间"))
                return@launch
            }

            _uiState.value = state.copy(isSaving = true, showPurposeConfirm = false)
            runCatching {
                createCashFlowRecordUseCase(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    purpose = state.purpose,
                    occurredAt = occurredAt,
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

