package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountListItemUiModel(
    val id: Long,
    val name: String,
    val colorName: String,
    val iconName: String,
    val balance: Long,
    val isHidden: Boolean = false,
    val isClosed: Boolean,
    val requiresReopenAndSettle: Boolean = false,
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
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    val errorMessage: String? = null,
    val retryToken: String? = null,
    val settings: PortableSettings = PortableSettings(),
    val showClosed: Boolean = false,
    val openAccounts: List<AccountListItemUiModel> = emptyList(),
    val closedAccounts: List<AccountListItemUiModel> = emptyList(),
    val savingsGoal: SavingsGoalUiModel? = null,
)

internal fun AccountsUiState.toAsyncContent(): AsyncContent<AccountsUiState> {
    errorMessage?.let { return AsyncContent.Error(it, retryToken) }
    if (!hasCommittedContent) return AsyncContent.Loading
    if (isRefreshing) return AsyncContent.Refreshing(this)
    if (openAccounts.isEmpty() && closedAccounts.isEmpty()) {
        return AsyncContent.Empty(EmptyKind.COMPLETELY_EMPTY)
    }
    return AsyncContent.Data(this)
}

private data class AccountsSnapshot(
    val settings: PortableSettings,
    val openAccounts: List<AccountListItemUiModel>,
    val closedAccounts: List<AccountListItemUiModel>,
    val savingsGoal: SavingsGoalUiModel?,
)

class AccountsViewModel(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()
    private val showClosedFlow = MutableStateFlow(false)
    private var observationJob: Job? = null
    private var retryGeneration = 0

    init {
        observeAccounts()
    }

    fun retry() {
        observeAccounts()
    }

    private fun observeAccounts() {
        observationJob?.cancel()
        val hasCommittedContent = _uiState.value.hasCommittedContent
        _uiState.update {
            it.copy(
                isLoading = !hasCommittedContent,
                isRefreshing = hasCommittedContent,
                errorMessage = null,
                retryToken = null,
            )
        }
        observationJob = viewModelScope.launch {
            try {
                val invalidations = combine(
                    accountRepository.observeAllAccounts(),
                    accountReminderSettingsRepository.observeReminderConfigs(),
                    portableSettingsRepository.observe(),
                    transactionRepository.observeChangeVersion(),
                    savingsGoalRepository.observe(),
                ) { _, _, _, _, _ ->
                    Unit
                }
                val snapshots = invalidations.map { readConsistentSnapshot() }
                combine(snapshots, showClosedFlow) { snapshot, showClosed ->
                    snapshot to showClosed
                }.collect { (snapshot, showClosed) ->
                    _uiState.value = AccountsUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        settings = snapshot.settings,
                        showClosed = showClosed,
                        openAccounts = snapshot.openAccounts,
                        closedAccounts = snapshot.closedAccounts,
                        savingsGoal = snapshot.savingsGoal,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                retryGeneration += 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "账户加载失败，请重试",
                        retryToken = "accounts:$retryGeneration",
                    )
                }
            }
        }
    }

    private suspend fun readConsistentSnapshot(): AccountsSnapshot =
        transactionRepository.runInTransaction {
            val accounts = accountRepository.queryAllAccounts()
            val reminderConfigs = accountReminderSettingsRepository.queryReminderConfigs()
            val settings = portableSettingsRepository.query()
            val goal = savingsGoalRepository.query()
            val balances = calculateAccountBalancesUseCase(accounts)
            val open = accounts.filterNot(Account::isClosed)
            val closed = accounts.filter(Account::isClosed)
            val issueIds = closed.asSequence()
                .filter { balances.getValue(it.id) != 0L }
                .mapTo(mutableSetOf(), Account::id)
            val totalAssets = balances.values.ledgerSumExact()
            AccountsSnapshot(
                settings = settings,
                openAccounts = buildItems(open, reminderConfigs, balances),
                closedAccounts = buildItems(closed, reminderConfigs, balances).map { account ->
                    account.copy(requiresReopenAndSettle = account.id in issueIds)
                },
                savingsGoal = goal?.let {
                    SavingsGoalUiModel(
                        targetAmount = it.targetAmount,
                        currentAmount = totalAssets,
                        isAchieved = totalAssets >= it.targetAmount,
                    )
                },
            )
        }

    fun toggleClosedVisibility() {
        showClosedFlow.update { !it }
    }

    private fun buildItems(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        balances: Map<Long, Long>,
    ): List<AccountListItemUiModel> = accounts
        .map { mapItem(it, reminderConfigs[it.id], balances.getValue(it.id)) }
        .sortedBy { it.displayOrder }

    private fun mapItem(
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
            isHidden = account.isHidden,
            isClosed = account.isClosed,
            isStale = AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfig ?: BalanceUpdateReminderConfig(),
            ),
            displayOrder = account.displayOrder,
        )
    }

}
