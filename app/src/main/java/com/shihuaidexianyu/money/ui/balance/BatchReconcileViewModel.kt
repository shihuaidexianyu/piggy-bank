package com.shihuaidexianyu.money.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
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
    val settings: AppSettings = AppSettings(),
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
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val updateBalanceUseCase: UpdateBalanceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatchReconcileUiState())
    val uiState: StateFlow<BatchReconcileUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<BatchReconcileEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    accountRepository.observeActiveAccounts(),
                    accountReminderSettingsRepository.observeReminderConfigs(),
                    settingsRepository.observeSettings(),
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
        val state = _uiState.value
        if (state.isSaving) return
        val selectedAccounts = state.accounts.filter { it.isSelected }
        if (selectedAccounts.isEmpty()) {
            effects.tryEmit(BatchReconcileEffect.ShowMessage("请至少选择一个账户"))
            return
        }

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val occurredAt = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis())
            val failedIds = mutableSetOf<Long>()
            val selectedIds = selectedAccounts.map(BatchReconcileAccountUiModel::accountId).toSet()
            var savedCount = 0
            selectedAccounts.forEach { account ->
                runCatching {
                    updateBalanceUseCase(
                        accountId = account.accountId,
                        actualBalance = account.systemBalance,
                        occurredAt = occurredAt,
                    )
                }.onSuccess {
                    savedCount += 1
                }.onFailure { throwable ->
                    failedIds += account.accountId
                    android.util.Log.e("BatchReconcileViewModel", "Failed to reconcile account ${account.accountId}", throwable)
                }
            }

            if (failedIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(BatchReconcileEffect.Saved(savedCount))
            } else {
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

    private suspend fun buildItems(
        accounts: List<AccountEntity>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): List<BatchReconcileAccountUiModel> = withContext(Dispatchers.Default) {
        accounts.filter { account ->
            AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
            )
        }.map { account ->
            BatchReconcileAccountUiModel(
                accountId = account.id,
                name = account.name,
                systemBalance = calculateCurrentBalanceUseCase(account.id),
                lastBalanceUpdateAt = account.lastBalanceUpdateAt,
            )
        }
    }
}
