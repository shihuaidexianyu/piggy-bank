package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ReminderNextDueCalculator
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.RecordValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateReminderUiState(
    val name: String = "",
    val type: ReminderType = ReminderType.MANUAL,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
    val amountText: String = "",
    val periodType: ReminderPeriodType = ReminderPeriodType.MONTHLY,
    val periodCustomDays: String = "30",
    val anchorDateText: String,
    val anchorTimeText: String,
    val anchorError: String? = null,
    val isSaving: Boolean = false,
)

sealed interface CreateReminderEffect {
    data object Saved : CreateReminderEffect
    data class ShowMessage(override val message: String) : CreateReminderEffect, UiEffect.HasMessage
}

class CreateReminderViewModel(
    private val accountRepository: AccountRepository,
    private val createReminderUseCase: CreateReminderUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    private val defaultAnchor = ReminderNextDueCalculator.defaultFutureAnchor(
        clockProvider.nowMillis(),
        zoneIdProvider.zoneId(),
    )
    private val defaultDraft = formatReminderAnchor(defaultAnchor, zoneIdProvider.zoneId())
    private val _uiState = MutableStateFlow(
        CreateReminderUiState(
            name = savedStateHandle[KEY_NAME] ?: "",
            type = savedStateHandle.get<String>(KEY_TYPE)?.let(ReminderType::fromValue) ?: ReminderType.MANUAL,
            selectedAccountId = savedStateHandle[KEY_ACCOUNT],
            direction = savedStateHandle.get<String>(KEY_DIRECTION)?.let(CashFlowDirection::fromValue)
                ?: CashFlowDirection.OUTFLOW,
            amountText = savedStateHandle[KEY_AMOUNT] ?: "",
            periodType = savedStateHandle.get<String>(KEY_PERIOD)?.let(ReminderPeriodType::fromValue)
                ?: ReminderPeriodType.MONTHLY,
            periodCustomDays = savedStateHandle[KEY_CUSTOM_DAYS] ?: "30",
            anchorDateText = savedStateHandle[KEY_ANCHOR_DATE] ?: defaultDraft.first,
            anchorTimeText = savedStateHandle[KEY_ANCHOR_TIME] ?: defaultDraft.second,
        ),
    )
    val uiState: StateFlow<CreateReminderUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<CreateReminderEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.queryOpenAccounts()
            val selected = _uiState.value.selectedAccountId
                ?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
            setState(_uiState.value.copy(accounts = accounts.toAccountOptionUiModels(), selectedAccountId = selected))
        }
    }

    fun updateName(value: String) = setState(_uiState.value.copy(name = value))
    fun updateType(value: ReminderType) = setState(_uiState.value.copy(type = value))
    fun updateAccount(id: Long) = setState(_uiState.value.copy(selectedAccountId = id))
    fun updateDirection(value: CashFlowDirection) = setState(_uiState.value.copy(direction = value))
    fun updateAmount(value: String) = setState(_uiState.value.copy(amountText = value))
    fun updatePeriodType(value: ReminderPeriodType) = setState(_uiState.value.copy(periodType = value))
    fun updatePeriodCustomDays(value: String) = setState(_uiState.value.copy(periodCustomDays = value))
    fun updateAnchorDate(value: String) = setState(_uiState.value.copy(anchorDateText = value, anchorError = null))
    fun updateAnchorTime(value: String) = setState(_uiState.value.copy(anchorTimeText = value, anchorError = null))

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.userMessage("请选择账户"))); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.userMessage("请输入有效金额"))); return@launch }
            runCatching { RecordValidator.requireReminderName(state.name) }
                .getOrElse { error -> effects.emit(CreateReminderEffect.ShowMessage(error.userMessage("请输入名称"))); return@launch }
            val anchor = parseReminderAnchor(
                state.anchorDateText,
                state.anchorTimeText,
                state.periodType,
                state.periodCustomDays,
                zoneIdProvider.zoneId(),
            ).getOrElse { error ->
                setState(_uiState.value.copy(anchorError = error.message ?: "请输入有效的首次时间"))
                return@launch
            }

            setState(state.copy(isSaving = true))
            runCatching {
                createReminderUseCase(
                    name = state.name,
                    type = state.type,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    periodType = state.periodType,
                    periodValue = anchor.periodValue,
                    periodMonth = anchor.periodMonth,
                    anchorDueAt = anchor.anchorDueAt,
                )
            }.onSuccess {
                effects.emit(CreateReminderEffect.Saved)
            }.onFailure { throwable ->
                val message = throwable.message ?: "保存失败"
                setState(_uiState.value.copy(isSaving = false, anchorError = message.takeIf { "首次" in it }))
                if ("首次" !in message) effects.emit(CreateReminderEffect.ShowMessage(message))
            }
        }
    }

    private fun setState(state: CreateReminderUiState) {
        _uiState.value = state
        savedStateHandle[KEY_NAME] = state.name
        savedStateHandle[KEY_TYPE] = state.type.value
        savedStateHandle[KEY_ACCOUNT] = state.selectedAccountId
        savedStateHandle[KEY_DIRECTION] = state.direction.value
        savedStateHandle[KEY_AMOUNT] = state.amountText
        savedStateHandle[KEY_PERIOD] = state.periodType.value
        savedStateHandle[KEY_CUSTOM_DAYS] = state.periodCustomDays
        savedStateHandle[KEY_ANCHOR_DATE] = state.anchorDateText
        savedStateHandle[KEY_ANCHOR_TIME] = state.anchorTimeText
    }

    private companion object {
        const val KEY_NAME = "reminder.create.name"
        const val KEY_TYPE = "reminder.create.type"
        const val KEY_ACCOUNT = "reminder.create.account"
        const val KEY_DIRECTION = "reminder.create.direction"
        const val KEY_AMOUNT = "reminder.create.amount"
        const val KEY_PERIOD = "reminder.create.period"
        const val KEY_CUSTOM_DAYS = "reminder.create.customDays"
        const val KEY_ANCHOR_DATE = "reminder.create.anchorDate"
        const val KEY_ANCHOR_TIME = "reminder.create.anchorTime"
    }
}
