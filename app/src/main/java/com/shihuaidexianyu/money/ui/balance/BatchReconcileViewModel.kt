package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.savedOperationId
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.PendingFormTerminal
import com.shihuaidexianyu.money.ui.common.PENDING_FORM_TERMINAL_KEY
import com.shihuaidexianyu.money.ui.common.pendingFormTerminal
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchReconcileAccountUiModel(
    val accountId: Long,
    val name: String,
    val systemBalance: Long,
    val lastBalanceUpdateAt: Long?,
    val isSelected: Boolean = true,
    val isFailed: Boolean = false,
)

data class BatchReconcileUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val settings: PortableSettings = PortableSettings(),
    val accounts: List<BatchReconcileAccountUiModel> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val pendingTerminal: PendingFormTerminal? = null,
) {
    val selectedCount: Int
        get() = accounts.count { it.isSelected }
}

sealed interface BatchReconcileEffect {
    data class ShowMessage(
        override val message: String,
    ) : BatchReconcileEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class BatchReconcileViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val operationIdFactory: LedgerOperationIdFactory,
    private val clockProvider: ClockProvider,
) : ViewModel() {
    private var draft = savedStateHandle.get<BatchReconcileDraft>(DRAFT_KEY)
        ?: BatchReconcileDraft()
    private val _uiState = MutableStateFlow(
        BatchReconcileUiState(
            pendingTerminal = savedStateHandle[PENDING_FORM_TERMINAL_KEY],
        ),
    )
    val uiState: StateFlow<BatchReconcileUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BatchReconcileEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var saveInFlight = false
    private var observationJob: Job? = null

    init {
        observeAccounts()
    }

    fun retryLoad() {
        observeAccounts()
    }

    private fun observeAccounts() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
        observationJob = viewModelScope.launch {
            try {
                combine(
                    accountRepository.observeOpenAccounts(),
                    accountReminderSettingsRepository.observeReminderConfigs(),
                    portableSettingsRepository.observe(),
                    transactionRepository.observeChangeVersion(),
                ) { accounts, reminderConfigs, settings, _ ->
                    Triple(accounts, reminderConfigs, settings)
                }.collect { (accounts, reminderConfigs, settings) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        settings = settings,
                        accounts = buildItems(accounts, reminderConfigs),
                        isDirty = draft.isDirty,
                        loadErrorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("BatchReconcileViewModel", "Failed to load stale accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "批量核对加载失败，请重试",
                )
            }
        }
    }

    fun toggleAccount(accountId: Long) {
        if (_uiState.value.isSaving) return
        val next = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { account ->
                if (account.accountId == accountId) {
                    account.copy(isSelected = !account.isSelected, isFailed = false)
                } else {
                    account
                }
            },
            isDirty = true,
        )
        _uiState.value = next
        persistDraft(
            draft.copy(
                selectedAccountIds = next.accounts
                    .filter(BatchReconcileAccountUiModel::isSelected)
                    .map(BatchReconcileAccountUiModel::accountId),
                isDirty = true,
            ),
        )
    }

    fun saveSelected() {
        if (saveInFlight || _uiState.value.pendingTerminal != null) return
        saveInFlight = true
        val state = _uiState.value
        val selectedAccounts = state.accounts.filter { it.isSelected }
        if (selectedAccounts.isEmpty()) {
            saveInFlight = false
            effects.tryEmit(BatchReconcileEffect.ShowMessage("请至少选择一个账户"))
            return
        }

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val occurredAt = draft.occurredAtMillis
                ?: DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()).also { timestamp ->
                    persistDraft(draft.copy(occurredAtMillis = timestamp))
                }
            val failedIds = mutableSetOf<Long>()
            val selectedIds = selectedAccounts.map(BatchReconcileAccountUiModel::accountId).toSet()
            var savedCount = 0
            selectedAccounts.forEach { account ->
                runCatching {
                    updateBalanceUseCase(
                        accountId = account.accountId,
                        actualBalance = actualBalanceFor(account.accountId, account.systemBalance),
                        occurredAt = occurredAt,
                        operationId = operationIdFor(account.accountId),
                    )
                }.onSuccess {
                    savedCount += 1
                }.onFailure { throwable ->
                    failedIds += account.accountId
                    runCatching {
                        android.util.Log.e(
                            "BatchReconcileViewModel",
                            "Failed to reconcile account ${account.accountId}",
                            throwable,
                        )
                    }
                }
            }

            if (failedIds.isEmpty()) {
                setPendingTerminal(
                    pendingFormTerminal(
                        kind = FormTerminalKind.SAVED,
                        count = savedCount,
                    ),
                )
            } else {
                saveInFlight = false
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    accounts = _uiState.value.accounts
                        .filterNot { it.accountId !in failedIds && it.accountId in selectedIds }
                        .map { it.copy(isFailed = it.accountId in failedIds, isSelected = it.accountId in failedIds) },
                )
                persistDraft(
                    draft.copy(
                        selectedAccountIds = failedIds.toList(),
                        isDirty = true,
                    ),
                )
                effects.emit(BatchReconcileEffect.ShowMessage("部分账户保存失败，请重试"))
            }
        }
    }

    private fun operationIdFor(accountId: Long): String {
        return draft.operationIds[accountId] ?: savedOperationId(
            existing = null,
            factory = operationIdFactory,
        ).also { operationId ->
            persistDraft(draft.copy(operationIds = draft.operationIds + (accountId to operationId)))
        }
    }

    fun ackTerminal(token: String) {
        if (_uiState.value.pendingTerminal?.token != token) return
        savedStateHandle.remove<PendingFormTerminal>(PENDING_FORM_TERMINAL_KEY)
        _uiState.value = _uiState.value.copy(pendingTerminal = null)
    }

    private fun setPendingTerminal(terminal: PendingFormTerminal) {
        savedStateHandle[PENDING_FORM_TERMINAL_KEY] = terminal
        _uiState.value = _uiState.value.copy(isSaving = false, pendingTerminal = terminal)
    }

    private fun actualBalanceFor(accountId: Long, currentBalance: Long): Long {
        return draft.actualBalances[accountId]
            ?: currentBalance.also { actualBalance ->
                persistDraft(draft.copy(actualBalances = draft.actualBalances + (accountId to actualBalance)))
            }
    }

    private companion object {
        const val DRAFT_KEY = "batch_reconcile_draft"
    }

    private fun persistDraft(next: BatchReconcileDraft) {
        draft = next
        savedStateHandle[DRAFT_KEY] = next
    }

    private suspend fun buildItems(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): List<BatchReconcileAccountUiModel> = withContext(Dispatchers.Default) {
        val staleAccounts = accounts.filter { account ->
            AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
            )
        }
        val balances = calculateAccountBalancesUseCase(staleAccounts)
        staleAccounts.map { account ->
            BatchReconcileAccountUiModel(
                accountId = account.id,
                name = account.name,
                systemBalance = balances[account.id] ?: account.initialBalance,
                lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                isSelected = !draft.isDirty || account.id in draft.selectedAccountIds,
            )
        }
    }
}
