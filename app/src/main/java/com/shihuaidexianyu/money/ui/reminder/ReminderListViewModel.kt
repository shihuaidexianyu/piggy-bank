package com.shihuaidexianyu.money.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.HomeDashboardSnapshot
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UndoSkipReminderUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.io.Serializable
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    // Structured period data; the list screen formats it via string resources.
    val periodType: ReminderPeriodType,
    val periodValue: Int,
    val periodMonth: Int?,
    val nextDueFormatted: String,
    val isEnabled: Boolean,
    val isOverdue: Boolean,
    val accountId: Long,
    val accountName: String = "",
    val direction: String,
    val amount: Long,
    val nextDueAt: Long,
    val canMutate: Boolean = true,
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
    val lastBalanceUpdateAt: Long? = null,
)

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val balanceReminders: List<BalanceReminderUiModel> = emptyList(),
    val dueReminders: List<ReminderUiModel> = emptyList(),
    val upcomingReminders: List<ReminderUiModel> = emptyList(),
    val pausedReminders: List<ReminderUiModel> = emptyList(),
    val pendingSkip: PendingReminderSkipEffect? = null,
)

data class PendingReminderSkipEffect(
    val token: String,
    val undoToken: ReminderSkipUndoToken,
) : Serializable

sealed interface ReminderListEffect {
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : ReminderListEffect, UiEffect.HasMessage
    data object DeleteFailed : ReminderListEffect
}

class ReminderListViewModel(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val skipReminderUseCase: SkipReminderUseCase,
    private val undoSkipReminderUseCase: UndoSkipReminderUseCase,
    private val observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderListUiState(pendingSkip = savedStateHandle[PENDING_SKIP_KEY]))
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<ReminderListEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private val deleteInFlight = mutableSetOf<Long>()
    private val skipInFlight = mutableSetOf<Pair<Long, Long>>()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.observeAllReminders(),
                observeHomeDashboardUseCase(),
                accountRepository.observeAllAccounts(),
            ) { reminders, snapshot, accounts ->
                ReminderListSource(
                    reminders = reminders,
                    snapshot = snapshot,
                    closedAccountIds = accounts.asSequence()
                        .filter { it.isClosed }
                        .map { it.id }
                        .toSet(),
                    accountNames = accounts.associate { it.id to it.name },
                )
            }.collect { source ->
                val reminders = source.reminders
                val snapshot = source.snapshot
                val projection = partitionReminderModels(
                    reminders = reminders,
                    settings = snapshot.settings,
                    nowMillis = clockProvider.nowMillis(),
                    zoneId = zoneIdProvider.zoneId(),
                    closedAccountIds = source.closedAccountIds,
                    accountNames = source.accountNames,
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
                            ),
                            lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                        )
                    },
                    dueReminders = projection.due,
                    upcomingReminders = projection.upcoming,
                    pausedReminders = projection.paused,
                    pendingSkip = savedStateHandle[PENDING_SKIP_KEY],
                )
            }
        }
    }

    fun deleteReminder(id: Long) {
        // Deleting a reminder never touches the ledger, so it stays available even when the
        // reminder's account is closed (which disables edit/skip/process but not removal).
        val reminder = sequenceOf(
            _uiState.value.dueReminders,
            _uiState.value.upcomingReminders,
            _uiState.value.pausedReminders,
        ).flatten().firstOrNull { it.id == id }
        if (reminder == null || !deleteInFlight.add(id)) return
        viewModelScope.launch {
            try {
                deleteReminderUseCase(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effects.emit(ReminderListEffect.DeleteFailed)
            } finally {
                deleteInFlight.remove(id)
            }
        }
    }

    fun skipReminder(id: Long, expectedDueAt: Long) {
        val key = id to expectedDueAt
        if (_uiState.value.pendingSkip != null || skipInFlight.isNotEmpty()) return
        if (!skipInFlight.add(key)) return
        viewModelScope.launch {
            runCatching { skipReminderUseCase(id, expectedDueAt) }
                .onSuccess { undoToken ->
                    val pending = PendingReminderSkipEffect(UUID.randomUUID().toString(), undoToken)
                    savedStateHandle[PENDING_SKIP_KEY] = pending
                    _uiState.value = _uiState.value.copy(pendingSkip = pending)
                }
                .onFailure { effects.emit(ReminderListEffect.ShowMessage(it.message.orEmpty(), messageRes = R.string.reminder_skip_failed)) }
            skipInFlight.remove(key)
        }
    }

    fun undoSkip(token: ReminderSkipUndoToken) {
        viewModelScope.launch {
            when (undoSkipReminderUseCase(token)) {
                UndoReminderSkipResult.RESTORED, UndoReminderSkipResult.ALREADY_RESTORED -> Unit
                UndoReminderSkipResult.STALE -> effects.emit(
                    ReminderListEffect.ShowMessage("", messageRes = R.string.reminder_undo_stale),
                )
                UndoReminderSkipResult.NOT_FOUND -> effects.emit(
                    ReminderListEffect.ShowMessage("", messageRes = R.string.reminder_undo_not_found),
                )
            }
        }
    }

    fun ackPendingSkip(token: String) {
        if (_uiState.value.pendingSkip?.token != token) return
        savedStateHandle.remove<PendingReminderSkipEffect>(PENDING_SKIP_KEY)
        _uiState.value = _uiState.value.copy(pendingSkip = null)
    }

    private companion object {
        const val PENDING_SKIP_KEY = "pending_reminder_skip"
    }
}

internal fun partitionReminderModels(
    reminders: List<RecurringReminder>,
    settings: PortableSettings,
    nowMillis: Long,
    zoneId: ZoneId,
    closedAccountIds: Set<Long> = emptySet(),
    accountNames: Map<Long, String> = emptyMap(),
): ReminderListProjection {
    val models = reminders
        .sortedWith(compareBy<RecurringReminder> { it.nextDueAt }.thenBy { it.id })
        .map { reminder ->
            reminder.toUiModel(
                settings = settings,
                nowMillis = nowMillis,
                zoneId = zoneId,
                canMutate = reminder.accountId !in closedAccountIds,
                accountName = accountNames[reminder.accountId].orEmpty(),
            )
        }
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
    canMutate: Boolean = true,
    accountName: String = "",
): ReminderUiModel {
    return ReminderUiModel(
        id = id,
        name = name,
        type = ReminderType.fromValue(type),
        amountFormatted = AmountFormatter.format(amount, settings),
        periodType = ReminderPeriodType.fromValue(periodType),
        periodValue = periodValue,
        periodMonth = periodMonth,
        nextDueFormatted = DateTimeTextFormatter.format(nextDueAt, zoneId),
        isEnabled = isEnabled,
        isOverdue = isEnabled && nextDueAt <= nowMillis,
        accountId = accountId,
        accountName = accountName,
        direction = direction,
        amount = amount,
        nextDueAt = nextDueAt,
        canMutate = canMutate,
    )
}

private data class ReminderListSource(
    val reminders: List<RecurringReminder>,
    val snapshot: HomeDashboardSnapshot,
    val closedAccountIds: Set<Long>,
    val accountNames: Map<Long, String>,
)
