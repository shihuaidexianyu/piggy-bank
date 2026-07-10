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
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.Dispatchers
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
    val settings: PortableSettings = PortableSettings(),
    val accounts: List<BatchReconcileAccountUiModel> = emptyList(),
    val isSaving: Boolean = false,
) {
    val selectedCount: Int
        get() = accounts.count { it.isSelected }
}

sealed interface BatchReconcileEffect {
    data class Saved(val count: Int) : BatchReconcileEffect
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
    private val _uiState = MutableStateFlow(BatchReconcileUiState())
    val uiState: StateFlow<BatchReconcileUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BatchReconcileEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var saveInFlight = false

    init {
        viewModelScope.launch {
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
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("BatchReconcileViewModel", "Failed to load stale accounts", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleAccount(accountId: Long) {
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.map { account ->
                if (account.accountId == accountId) {
                    account.copy(isSelected = !account.isSelected, isFailed = false)
                } else {
                    account
                }
            },
        )
    }

    fun saveSelected() {
        if (saveInFlight) return
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
            val occurredAt = savedStateHandle.get<Long>(OCCURRED_AT_KEY)
                ?: DateTimeTextFormatter.floorToMinute(clockProvider.nowMillis()).also { timestamp ->
                    savedStateHandle[OCCURRED_AT_KEY] = timestamp
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
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(BatchReconcileEffect.Saved(savedCount))
            } else {
                saveInFlight = false
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    accounts = _uiState.value.accounts
                        .filterNot { it.accountId !in failedIds && it.accountId in selectedIds }
                        .map { it.copy(isFailed = it.accountId in failedIds, isSelected = it.accountId in failedIds) },
                )
                effects.emit(BatchReconcileEffect.ShowMessage("部分账户保存失败，请重试"))
            }
        }
    }

    private fun operationIdFor(accountId: Long): String {
        val key = "$OPERATION_ID_KEY_PREFIX$accountId"
        return savedOperationId(
            existing = savedStateHandle[key],
            factory = operationIdFactory,
        ).also { savedStateHandle[key] = it }
    }

    private fun actualBalanceFor(accountId: Long, currentBalance: Long): Long {
        val key = "$ACTUAL_BALANCE_KEY_PREFIX$accountId"
        return savedStateHandle.get<Long>(key)
            ?: currentBalance.also { savedStateHandle[key] = it }
    }

    private companion object {
        const val OCCURRED_AT_KEY = "batch_reconcile_occurred_at"
        const val OPERATION_ID_KEY_PREFIX = "batch_reconcile_operation_id:"
        const val ACTUAL_BALANCE_KEY_PREFIX = "batch_reconcile_actual_balance:"
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
            )
        }
    }
}
