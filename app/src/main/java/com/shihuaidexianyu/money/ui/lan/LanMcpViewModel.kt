package com.shihuaidexianyu.money.ui.lan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.repository.AiMutationJournalRepository
import com.shihuaidexianyu.money.domain.usecase.AiJournaledLedgerUseCase
import com.shihuaidexianyu.money.lan.LanPairedDevice
import com.shihuaidexianyu.money.lan.LanPairedDeviceStore
import com.shihuaidexianyu.money.lan.MoneyLanRuntime
import com.shihuaidexianyu.money.lan.MoneyLanRuntimeState
import com.shihuaidexianyu.money.lan.MoneyLanServerStatus
import com.shihuaidexianyu.money.lan.MoneyLanService
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.userMessage
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanMcpUiState(
    val runtime: MoneyLanRuntimeState = MoneyLanRuntimeState(),
    val allowWriteDraft: Boolean = true,
    val pairedDevices: List<LanPairedDevice> = emptyList(),
    val journalEntries: List<AiMutationJournalEntry> = emptyList(),
    val latestAppliedEntry: AiMutationJournalEntry? = null,
    val appliedCount: Int = 0,
    val isJournalActionRunning: Boolean = false,
) {
    val isRunning: Boolean
        get() = runtime.status == MoneyLanServerStatus.RUNNING ||
            runtime.status == MoneyLanServerStatus.STARTING
}

sealed interface LanMcpEffect : UiEffect.HasMessage {
    data class ShowMessage(override val message: String) : LanMcpEffect
}

class LanMcpViewModel(
    context: Context,
    journalRepository: AiMutationJournalRepository,
    private val lanPairedDeviceStore: LanPairedDeviceStore,
    private val aiJournaledLedgerUseCase: AiJournaledLedgerUseCase,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val allowWriteDraft = MutableStateFlow(true)
    private val isJournalActionRunning = MutableStateFlow(false)
    private val effects = MutableSharedFlow<LanMcpEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private val journalState = combine(
        journalRepository.observeRecent(JOURNAL_LIMIT),
        journalRepository.observeLatestApplied(),
    ) { entries, latestApplied -> JournalState(entries, latestApplied) }

    val uiState: StateFlow<LanMcpUiState> = combine(
        MoneyLanRuntime.state,
        allowWriteDraft,
        lanPairedDeviceStore.observeDevices(),
        journalState,
        journalRepository.observeAppliedCount(),
        isJournalActionRunning,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val journal = values[3] as JournalState
        LanMcpUiState(
            runtime = values[0] as MoneyLanRuntimeState,
            allowWriteDraft = values[1] as Boolean,
            pairedDevices = values[2] as List<LanPairedDevice>,
            journalEntries = journal.entries,
            latestAppliedEntry = journal.latestApplied,
            appliedCount = values[4] as Int,
            isJournalActionRunning = values[5] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LanMcpUiState(),
    )

    /** Answer a pending confirmation-style pairing request from the in-app dialog. */
    fun approvePairing() {
        val requestId = uiState.value.runtime.pendingPairRequestId ?: return
        MoneyLanRuntime.pairingResponder?.approve(requestId)
    }

    fun denyPairing() {
        val requestId = uiState.value.runtime.pendingPairRequestId ?: return
        MoneyLanRuntime.pairingResponder?.deny(requestId)
    }

    /** Revoke a paired device: drops its stored credential and any active session it holds. */
    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            runCatching {
                val responder = MoneyLanRuntime.pairingResponder
                if (responder != null) {
                    responder.revokeDevice(deviceId)
                } else {
                    lanPairedDeviceStore.remove(deviceId)
                }
            }.onSuccess {
                effects.emit(LanMcpEffect.ShowMessage(appContext.getString(R.string.lan_device_revoked)))
            }.onFailure { error ->
                effects.emit(
                    LanMcpEffect.ShowMessage(error.userMessage(appContext.getString(R.string.lan_journal_update_failed))),
                )
            }
        }
    }

    fun setAllowWrite(enabled: Boolean) {
        if (!uiState.value.isRunning) allowWriteDraft.value = enabled
    }

    fun startServer() {
        if (uiState.value.isRunning) return
        runCatching { MoneyLanService.start(appContext, allowWriteDraft.value) }
            .onFailure {
                effects.tryEmit(
                    LanMcpEffect.ShowMessage(it.userMessage(appContext.getString(R.string.lan_start_failed))),
                )
            }
    }

    fun stopServer() {
        if (!uiState.value.isRunning) return
        runCatching { MoneyLanService.stop(appContext) }
            .onFailure {
                effects.tryEmit(
                    LanMcpEffect.ShowMessage(it.userMessage(appContext.getString(R.string.lan_stop_failed))),
                )
            }
    }

    fun undoLatest() {
        if (isJournalActionRunning.value) return
        viewModelScope.launch {
            isJournalActionRunning.value = true
            runCatching {
                aiJournaledLedgerUseCase.undoLatest("phone:${UUID.randomUUID()}")
            }.onSuccess { result ->
                val message = when (result) {
                    UndoLatestAiMutationResult.Empty -> appContext.getString(R.string.lan_nothing_to_undo)
                    is UndoLatestAiMutationResult.Conflict -> result.message
                    is UndoLatestAiMutationResult.BatchConflict -> result.message
                    is UndoLatestAiMutationResult.Undone -> appContext.getString(
                        R.string.lan_undo_succeeded,
                        result.entry.summary,
                    )
                    is UndoLatestAiMutationResult.BatchUndone -> appContext.getString(
                        R.string.lan_undo_succeeded,
                        result.entry.summary,
                    )
                }
                effects.emit(LanMcpEffect.ShowMessage(message))
            }.onFailure { error ->
                effects.emit(
                    LanMcpEffect.ShowMessage(error.userMessage(appContext.getString(R.string.lan_undo_failed))),
                )
            }
            isJournalActionRunning.value = false
        }
    }

    fun discardLatest(entryId: Long) {
        if (isJournalActionRunning.value) return
        viewModelScope.launch {
            isJournalActionRunning.value = true
            runCatching { aiJournaledLedgerUseCase.discardLatest(entryId) }
                .onSuccess { discarded ->
                    effects.emit(
                        LanMcpEffect.ShowMessage(
                            appContext.getString(
                                if (discarded) R.string.lan_discard_succeeded else R.string.lan_stack_changed,
                            ),
                        ),
                    )
                }
                .onFailure { error ->
                    effects.emit(
                        LanMcpEffect.ShowMessage(
                            error.userMessage(appContext.getString(R.string.lan_journal_update_failed)),
                        ),
                    )
                }
            isJournalActionRunning.value = false
        }
    }

    private companion object {
        const val JOURNAL_LIMIT = 50
    }

    private data class JournalState(
        val entries: List<AiMutationJournalEntry>,
        val latestApplied: AiMutationJournalEntry?,
    )
}
