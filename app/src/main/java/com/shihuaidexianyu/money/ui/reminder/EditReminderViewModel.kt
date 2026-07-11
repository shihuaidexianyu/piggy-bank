package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModels
import com.shihuaidexianyu.money.ui.common.userMessage
import com.shihuaidexianyu.money.util.RecordValidator
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditReminderUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val name: String = "",
    val type: ReminderType = ReminderType.MANUAL,
    val accounts: List<AccountOptionUiModel> = emptyList(),
    val selectedAccountId: Long? = null,
    val direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
    val amountText: String = "",
    val periodType: ReminderPeriodType = ReminderPeriodType.MONTHLY,
    val periodCustomDays: String = "30",
    val anchorDateText: String = "",
    val anchorTimeText: String = "",
    val anchorError: String? = null,
    val isEnabled: Boolean = true,
    val scheduleDirty: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface EditReminderEffect {
    data class Saved(val shouldRequestNotificationPermission: Boolean) : EditReminderEffect
    data object Closed : EditReminderEffect
    data class ShowMessage(override val message: String) : EditReminderEffect, UiEffect.HasMessage
}

class EditReminderViewModel(
    private val reminderId: Long,
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditReminderUiState())
    val uiState: StateFlow<EditReminderUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditReminderEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false
    private var initiallyEnabled: Boolean? = null
    private var originalAnchorDueAt: Long = 0L
    private var originalPeriodType: ReminderPeriodType = ReminderPeriodType.MONTHLY
    private var originalPeriodValue: Int = 1
    private var originalPeriodMonth: Int? = null
    private var originalUpdatedAt: Long = 0L

    init {
        loadReminder()
    }

    fun retryLoad() {
        loadReminder()
    }

    private fun loadReminder() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
        viewModelScope.launch {
            try {
                val reminder = reminderRepository.getReminderById(reminderId)
                if (reminder == null) {
                    emitClosedOnce()
                    return@launch
                }
                val accounts = accountRepository.queryOpenAccounts()
                initiallyEnabled = reminder.isEnabled
                val periodType = ReminderPeriodType.fromValue(reminder.periodType)
                val anchorDraft = formatReminderAnchor(reminder.anchorDueAt, zoneIdProvider.zoneId())
                val restored = savedStateHandle.get<Boolean>(KEY_INITIALIZED) == true
                originalAnchorDueAt = if (restored) {
                    savedStateHandle[KEY_ORIGINAL_ANCHOR] ?: reminder.anchorDueAt
                } else reminder.anchorDueAt
                originalPeriodType = if (restored) {
                    savedStateHandle.get<String>(KEY_ORIGINAL_PERIOD_TYPE)
                        ?.let(ReminderPeriodType::fromValue) ?: periodType
                } else periodType
                originalPeriodValue = if (restored) {
                    savedStateHandle[KEY_ORIGINAL_PERIOD_VALUE] ?: reminder.periodValue
                } else reminder.periodValue
                originalPeriodMonth = if (restored) {
                    savedStateHandle[KEY_ORIGINAL_PERIOD_MONTH]
                } else reminder.periodMonth
                originalUpdatedAt = if (restored) {
                    savedStateHandle[KEY_ORIGINAL_UPDATED_AT] ?: reminder.updatedAt
                } else reminder.updatedAt
                setState(
                    EditReminderUiState(
                        isLoading = false,
                        name = if (restored) savedStateHandle[KEY_NAME] ?: reminder.name else reminder.name,
                        type = if (restored) {
                            savedStateHandle.get<String>(KEY_TYPE)?.let(ReminderType::fromValue)
                                ?: ReminderType.fromValue(reminder.type)
                        } else ReminderType.fromValue(reminder.type),
                        accounts = accounts.toAccountOptionUiModels(),
                        selectedAccountId = if (restored) savedStateHandle[KEY_ACCOUNT] ?: reminder.accountId else reminder.accountId,
                        direction = if (restored) {
                            savedStateHandle.get<String>(KEY_DIRECTION)?.let(CashFlowDirection::fromValue)
                                ?: CashFlowDirection.fromValue(reminder.direction)
                        } else CashFlowDirection.fromValue(reminder.direction),
                        amountText = if (restored) savedStateHandle[KEY_AMOUNT] ?: reminder.amount.toAmountText() else reminder.amount.toAmountText(),
                        periodType = if (restored) {
                            savedStateHandle.get<String>(KEY_PERIOD)?.let(ReminderPeriodType::fromValue) ?: periodType
                        } else periodType,
                        periodCustomDays = if (restored) {
                            savedStateHandle[KEY_CUSTOM_DAYS] ?: reminder.periodValue.toString()
                        } else if (periodType == ReminderPeriodType.CUSTOM_DAYS) reminder.periodValue.toString() else "30",
                        anchorDateText = if (restored) savedStateHandle[KEY_ANCHOR_DATE] ?: anchorDraft.first else anchorDraft.first,
                        anchorTimeText = if (restored) savedStateHandle[KEY_ANCHOR_TIME] ?: anchorDraft.second else anchorDraft.second,
                        isEnabled = if (restored) savedStateHandle[KEY_ENABLED] ?: reminder.isEnabled else reminder.isEnabled,
                        scheduleDirty = if (restored) savedStateHandle[KEY_SCHEDULE_DIRTY] ?: false else false,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditReminderViewModel", "Failed to load reminder", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "提醒加载失败，请重试",
                )
            }
        }
    }

    fun updateName(value: String) = setState(_uiState.value.copy(name = value))
    fun updateType(value: ReminderType) = setState(_uiState.value.copy(type = value))
    fun updateAccount(id: Long) = setState(_uiState.value.copy(selectedAccountId = id))
    fun updateDirection(value: CashFlowDirection) = setState(_uiState.value.copy(direction = value))
    fun updateAmount(value: String) = setState(_uiState.value.copy(amountText = value))
    fun updatePeriodType(value: ReminderPeriodType) = setState(
        _uiState.value.copy(periodType = value, scheduleDirty = true),
    )
    fun updatePeriodCustomDays(value: String) = setState(
        _uiState.value.copy(periodCustomDays = value, scheduleDirty = true),
    )
    fun updateAnchorDate(value: String) = setState(
        _uiState.value.copy(anchorDateText = value, anchorError = null, scheduleDirty = true),
    )
    fun updateAnchorTime(value: String) = setState(
        _uiState.value.copy(anchorTimeText = value, anchorError = null, scheduleDirty = true),
    )
    fun updateEnabled(value: Boolean) = setState(_uiState.value.copy(isEnabled = value))

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            val accountId = runCatching { RecordValidator.requireAccountId(state.selectedAccountId) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.userMessage("请选择账户"))); return@launch }
            val amount = runCatching { RecordValidator.requireAmount(state.amountText) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.userMessage("请输入有效金额"))); return@launch }
            runCatching { RecordValidator.requireReminderName(state.name) }
                .getOrElse { error -> effects.emit(EditReminderEffect.ShowMessage(error.userMessage("请输入名称"))); return@launch }
            val anchor = if (state.scheduleDirty) {
                parseReminderAnchor(
                    state.anchorDateText,
                    state.anchorTimeText,
                    state.periodType,
                    state.periodCustomDays,
                    zoneIdProvider.zoneId(),
                ).getOrElse { error ->
                    setState(_uiState.value.copy(anchorError = error.message ?: "请输入有效的首次时间"))
                    return@launch
                }
            } else {
                ReminderAnchorInput(originalAnchorDueAt, originalPeriodValue, originalPeriodMonth)
            }
            val requestedPeriodType = if (state.scheduleDirty) state.periodType else originalPeriodType

            setState(state.copy(isSaving = true))
            runCatching {
                updateReminderUseCase(
                    reminderId = reminderId,
                    name = state.name,
                    type = state.type,
                    accountId = accountId,
                    direction = state.direction,
                    amount = amount,
                    periodType = requestedPeriodType,
                    periodValue = anchor.periodValue,
                    periodMonth = anchor.periodMonth,
                    anchorDueAt = anchor.anchorDueAt.takeIf { state.scheduleDirty },
                    isEnabled = state.isEnabled,
                    expectedUpdatedAt = originalUpdatedAt,
                )
            }.onSuccess {
                effects.emit(
                    EditReminderEffect.Saved(
                        shouldRequestNotificationPermission = initiallyEnabled == false && state.isEnabled,
                    ),
                )
            }.onFailure { throwable ->
                val lookup = runCatching { reminderRepository.getReminderById(reminderId) }
                if (lookup.isSuccess && lookup.getOrNull() == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                val message = throwable.message ?: "保存失败"
                setState(_uiState.value.copy(isSaving = false, anchorError = message.takeIf { "首次" in it }))
                if ("首次" !in message) effects.emit(EditReminderEffect.ShowMessage(message))
            }
        }
    }

    private fun setState(state: EditReminderUiState) {
        _uiState.value = state
        if (state.isLoading) return
        savedStateHandle[KEY_INITIALIZED] = true
        savedStateHandle[KEY_NAME] = state.name
        savedStateHandle[KEY_TYPE] = state.type.value
        savedStateHandle[KEY_ACCOUNT] = state.selectedAccountId
        savedStateHandle[KEY_DIRECTION] = state.direction.value
        savedStateHandle[KEY_AMOUNT] = state.amountText
        savedStateHandle[KEY_PERIOD] = state.periodType.value
        savedStateHandle[KEY_CUSTOM_DAYS] = state.periodCustomDays
        savedStateHandle[KEY_ANCHOR_DATE] = state.anchorDateText
        savedStateHandle[KEY_ANCHOR_TIME] = state.anchorTimeText
        savedStateHandle[KEY_ENABLED] = state.isEnabled
        savedStateHandle[KEY_SCHEDULE_DIRTY] = state.scheduleDirty
        savedStateHandle[KEY_ORIGINAL_ANCHOR] = originalAnchorDueAt
        savedStateHandle[KEY_ORIGINAL_PERIOD_TYPE] = originalPeriodType.value
        savedStateHandle[KEY_ORIGINAL_PERIOD_VALUE] = originalPeriodValue
        savedStateHandle[KEY_ORIGINAL_PERIOD_MONTH] = originalPeriodMonth
        savedStateHandle[KEY_ORIGINAL_UPDATED_AT] = originalUpdatedAt
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditReminderEffect.Closed)
    }

    private fun Long.toAmountText(): String = BigDecimal.valueOf(this, 2)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()

    private companion object {
        const val KEY_INITIALIZED = "reminder.edit.initialized"
        const val KEY_NAME = "reminder.edit.name"
        const val KEY_TYPE = "reminder.edit.type"
        const val KEY_ACCOUNT = "reminder.edit.account"
        const val KEY_DIRECTION = "reminder.edit.direction"
        const val KEY_AMOUNT = "reminder.edit.amount"
        const val KEY_PERIOD = "reminder.edit.period"
        const val KEY_CUSTOM_DAYS = "reminder.edit.customDays"
        const val KEY_ANCHOR_DATE = "reminder.edit.anchorDate"
        const val KEY_ANCHOR_TIME = "reminder.edit.anchorTime"
        const val KEY_ENABLED = "reminder.edit.enabled"
        const val KEY_SCHEDULE_DIRTY = "reminder.edit.scheduleDirty"
        const val KEY_ORIGINAL_ANCHOR = "reminder.edit.originalAnchor"
        const val KEY_ORIGINAL_PERIOD_TYPE = "reminder.edit.originalPeriodType"
        const val KEY_ORIGINAL_PERIOD_VALUE = "reminder.edit.originalPeriodValue"
        const val KEY_ORIGINAL_PERIOD_MONTH = "reminder.edit.originalPeriodMonth"
        const val KEY_ORIGINAL_UPDATED_AT = "reminder.edit.originalUpdatedAt"
    }
}
