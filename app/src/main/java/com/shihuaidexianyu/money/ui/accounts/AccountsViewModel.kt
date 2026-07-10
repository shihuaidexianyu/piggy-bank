package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.SavingsGoalWithProgress
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalsUseCase
import com.shihuaidexianyu.money.util.AccountStatusUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountListItemUiModel(
    val id: Long,
    val name: String,
    val colorName: String,
    val iconName: String,
    val balance: Long,
    val isClosed: Boolean,
    val isStale: Boolean,
    val displayOrder: Int,
)

data class SavingsGoalUiModel(
    val targetAmount: Long,
    val currentAmount: Long,
    val isAchieved: Boolean,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val showClosed: Boolean = false,
    val openAccounts: List<AccountListItemUiModel> = emptyList(),
    val closedAccounts: List<AccountListItemUiModel> = emptyList(),
    val savingsGoal: SavingsGoalUiModel? = null,
)

private data class AccountsSnapshot(
    val settings: AppSettings,
    val openAccounts: List<AccountListItemUiModel>,
    val closedAccounts: List<AccountListItemUiModel>,
    val savingsGoal: SavingsGoalUiModel?,
)

class AccountsViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val observeSavingsGoalsUseCase: ObserveSavingsGoalsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private val showClosedFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                val snapshotFlow = combine(
                    accountRepository.observeOpenAccounts(),
                    accountRepository.observeClosedAccounts(),
                    accountReminderSettingsRepository.observeReminderConfigs(),
                    settingsRepository.observeSettings(),
                    transactionRepository.observeChangeVersion(),
                ) { open, closed, reminderConfigs, settings, _ ->
                    AccountsSnapshot(
                        settings = settings,
                        openAccounts = buildItems(open, reminderConfigs),
                        closedAccounts = buildItems(closed, reminderConfigs),
                        savingsGoal = null,
                    )
                }
                val goalsFlow = observeSavingsGoalsUseCase().map { goals ->
                    goals.firstOrNull()?.toUiModel()
                }
                combine(snapshotFlow, goalsFlow) { snapshot, goal ->
                    snapshot.copy(savingsGoal = goal)
                }.combine(showClosedFlow) { snapshot, showClosed ->
                    AccountsUiState(
                        isLoading = false,
                        settings = snapshot.settings,
                        showClosed = showClosed,
                        openAccounts = snapshot.openAccounts,
                        closedAccounts = snapshot.closedAccounts,
                        savingsGoal = snapshot.savingsGoal,
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleClosedVisibility() {
        showClosedFlow.update { !it }
    }

    private suspend fun buildItems(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): List<AccountListItemUiModel> = withContext(Dispatchers.Default) {
        val balances = calculateAccountBalancesUseCase(accounts)
        val items = accounts.map { mapItem(it, reminderConfigs[it.id], balances[it.id] ?: it.initialBalance) }
        items.sortedBy { it.displayOrder }
    }

    private suspend fun mapItem(
        account: Account,
        reminderConfig: BalanceUpdateReminderConfig?,
        balance: Long,
    ): AccountListItemUiModel {
        return AccountListItemUiModel(
            id = account.id,
            name = account.name,
            colorName = account.colorName,
            iconName = account.iconName,
            balance = balance,
            isClosed = account.isClosed,
            isStale = AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfig ?: BalanceUpdateReminderConfig(),
            ),
            displayOrder = account.displayOrder,
        )
    }

    private fun SavingsGoalWithProgress.toUiModel(): SavingsGoalUiModel =
        SavingsGoalUiModel(
            targetAmount = targetAmount,
            currentAmount = currentAmount,
            isAchieved = isAchieved,
        )
}
