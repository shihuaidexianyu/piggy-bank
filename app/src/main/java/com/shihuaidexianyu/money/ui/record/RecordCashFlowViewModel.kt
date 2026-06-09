package com.shihuaidexianyu.money.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
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
    val purpose: String = "",
    val occurredAtMillis: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    val purposeSuggestions: List<String> = emptyList(),
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
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val createCashFlowRecordUseCase: CreateCashFlowRecordUseCase,
    private val processDueReminderUseCase: ProcessDueReminderUseCase? = null,
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
                    accounts = accounts.map { account ->
                        account.toAccountOptionUiModel(
                            balance = calculateCurrentBalanceUseCase(account.id),
                        )
                    },
                    selectedAccountId = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id,
                )
                refreshPurposeSuggestions()
            } catch (e: Exception) {
                android.util.Log.e("RecordCashFlowViewModel", "Failed to load accounts", e)
            }
        }
    }

    fun updateAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
        refreshPurposeSuggestions()
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amountText = value)
    }

    fun updatePurpose(value: String) {
        _uiState.value = _uiState.value.copy(purpose = value)
    }

    fun applyPurposeSuggestion(value: String) {
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

    private fun refreshPurposeSuggestions() {
        val accountId = _uiState.value.selectedAccountId
        viewModelScope.launch {
            runCatching {
                transactionRepository.queryRecentCashFlowPurposes(
                    direction = direction.value,
                    accountId = accountId,
                    limit = 6,
                ).ifEmpty {
                    transactionRepository.queryRecentCashFlowPurposes(
                        direction = direction.value,
                        accountId = null,
                        limit = 6,
                    )
                }
            }.onSuccess { suggestions ->
                _uiState.value = _uiState.value.copy(purposeSuggestions = suggestions)
            }
        }
    }

    fun save(confirmBlankPurpose: Boolean = false) {
        val state = _uiState.value
        if (state.purpose.isBlank() && !confirmBlankPurpose) {
            _uiState.value = state.copy(showPurposeConfirm = true)
            return
        }

        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("请选择账户"))); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("请输入有效金额"))); return@launch }
            runCatching { RecordValidator.requireOccurredAt(state.occurredAtMillis) }
                .getOrElse { error -> effects.emit(RecordCashFlowEffect.ShowMessage(error.userMessage("时间不能晚于当前时间"))); return@launch }

            _uiState.value = state.copy(isSaving = true, showPurposeConfirm = false)
            runCatching {
                if (reminderId != null && processDueReminderUseCase != null) {
                    processDueReminderUseCase(
                        reminderId = reminderId,
                        occurredAt = state.occurredAtMillis,
                        amount = amount,
                        purpose = state.purpose,
                    )
                } else {
                    createCashFlowRecordUseCase(
                        accountId = accountId,
                        direction = direction,
                        amount = amount,
                        purpose = state.purpose,
                        occurredAt = state.occurredAtMillis,
                    )
                }
            }.onSuccess {
                effects.emit(RecordCashFlowEffect.Saved)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(RecordCashFlowEffect.ShowMessage(throwable.message ?: "保存失败"))
            }
        }
    }
}
