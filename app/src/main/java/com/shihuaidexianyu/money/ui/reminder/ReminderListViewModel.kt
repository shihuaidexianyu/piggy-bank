package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UndoSkipReminderUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReminderUiModel(
    val id: Long,
    val name: String,
    val type: ReminderType,
    val amountFormatted: String,
    val periodDescription: String,
    val nextDueFormatted: String,
    val isEnabled: Boolean,
    val isOverdue: Boolean,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val nextDueAt: Long,
)

data class ReminderListProjection(
    val due: List<ReminderUiModel>,
    val upcoming: List<ReminderUiModel>,
    val paused: List<ReminderUiModel>,
)

data class BalanceReminderUiModel(
    val accountId: Long,
    val name: String,
    val currentBalanceFormatted: String,
    val lastBalanceUpdateText: String,
)

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val balanceReminders: List<BalanceReminderUiModel> = emptyList(),
    val dueReminders: List<ReminderUiModel> = emptyList(),
    val upcomingReminders: List<ReminderUiModel> = emptyList(),
    val pausedReminders: List<ReminderUiModel> = emptyList(),
)

sealed interface ReminderListEffect {
    data class Skipped(val token: ReminderSkipUndoToken) : ReminderListEffect
    data class ShowMessage(override val message: String) : ReminderListEffect, UiEffect.HasMessage
}

class ReminderListViewModel(
    private val reminderRepository: RecurringReminderRepository,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val skipReminderUseCase: SkipReminderUseCase,
    private val undoSkipReminderUseCase: UndoSkipReminderUseCase,
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val devicePreferencesRepository: DevicePreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderListUiState())
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<ReminderListEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.observeAllReminders(),
                observeHomeDashboardUseCase(),
                devicePreferencesRepository.observe(),
            ) { reminders, snapshot, devicePreferences -> Triple(reminders, snapshot, devicePreferences) }
                .collect { (reminders, snapshot, devicePreferences) ->
                    val visibility = AmountPrivacy.from(devicePreferences)
                        .visibilityFor(AmountSurface.IN_APP)
                    val projection = partitionReminderModels(
                        reminders = reminders,
                        settings = snapshot.settings,
                        nowMillis = clockProvider.nowMillis(),
                        zoneId = zoneIdProvider.zoneId(),
                        amountVisibility = visibility,
                    )
                    _uiState.value = ReminderListUiState(
                        isLoading = false,
                        balanceReminders = snapshot.staleAccounts.map { account ->
                            BalanceReminderUiModel(
                                accountId = account.id,
                                name = account.name,
                                currentBalanceFormatted = AmountFormatter.format(
                                    snapshot.accountBalances[account.id] ?: 0L,
                                    snapshot.settings,
                                    visibility,
                                ),
                                lastBalanceUpdateText = account.lastBalanceUpdateAt?.let {
                                    "最近核对 ${DateTimeTextFormatter.format(it)}"
                                } ?: "尚未核对",
                            )
                        },
                        dueReminders = projection.due,
                        upcomingReminders = projection.upcoming,
                        pausedReminders = projection.paused,
                    )
                }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { deleteReminderUseCase(id) }
    }

    fun skipReminder(id: Long, expectedDueAt: Long) {
        viewModelScope.launch {
            runCatching { skipReminderUseCase(id, expectedDueAt) }
                .onSuccess { effects.emit(ReminderListEffect.Skipped(it)) }
                .onFailure { effects.emit(ReminderListEffect.ShowMessage(it.message ?: "跳过失败，请刷新后重试")) }
        }
    }

    fun undoSkip(token: ReminderSkipUndoToken) {
        viewModelScope.launch {
            when (undoSkipReminderUseCase(token)) {
                UndoReminderSkipResult.RESTORED -> Unit
                UndoReminderSkipResult.STALE -> effects.emit(
                    ReminderListEffect.ShowMessage("提醒已发生变化，无法撤销"),
                )
                UndoReminderSkipResult.NOT_FOUND -> effects.emit(
                    ReminderListEffect.ShowMessage("提醒已删除，无法撤销"),
                )
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

internal fun partitionReminderModels(
    reminders: List<RecurringReminder>,
    settings: PortableSettings,
    nowMillis: Long,
    zoneId: ZoneId,
    amountVisibility: AmountVisibility = AmountVisibility.VISIBLE,
): ReminderListProjection {
    val models = reminders
        .sortedWith(compareBy<RecurringReminder> { it.nextDueAt }.thenBy { it.id })
        .map { it.toUiModel(settings, nowMillis, zoneId, amountVisibility) }
    return ReminderListProjection(
        due = models.filter { it.isEnabled && it.isOverdue },
        upcoming = models.filter { it.isEnabled && !it.isOverdue },
        paused = models.filterNot { it.isEnabled },
    )
}

internal fun RecurringReminder.toUiModel(
    settings: PortableSettings,
    nowMillis: Long,
    zoneId: ZoneId,
    amountVisibility: AmountVisibility = AmountVisibility.VISIBLE,
): ReminderUiModel {
    val periodDescription = when (ReminderPeriodType.fromValue(periodType)) {
        ReminderPeriodType.MONTHLY -> "每月${periodValue}日"
        ReminderPeriodType.YEARLY -> "每年${periodMonth ?: 1}月${periodValue}日"
        ReminderPeriodType.CUSTOM_DAYS -> "每${periodValue}天"
    }
    val nextDue = Instant.ofEpochMilli(nextDueAt).atZone(zoneId)
    return ReminderUiModel(
        id = id,
        name = name,
        type = ReminderType.fromValue(type),
        amountFormatted = AmountFormatter.format(amount, settings, amountVisibility),
        periodDescription = periodDescription,
        nextDueFormatted = nextDue.format(dateFormatter),
        isEnabled = isEnabled,
        isOverdue = isEnabled && nextDueAt <= nowMillis,
        accountId = accountId,
        direction = direction,
        amount = amount,
        nextDueAt = nextDueAt,
    )
}
