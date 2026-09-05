package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class ReorderAccountItemUiModel(
    val id: Long,
    val name: String,
    val colorName: String,
    val iconName: String,
    val kind: AccountKind,
    val balance: Long,
    val lastUsedAt: Long?,
    val isHidden: Boolean = false,
)

data class ReorderAccountsUiState(
    val isLoading: Boolean = true,
    val loadErrorMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val accounts: List<ReorderAccountItemUiModel> = emptyList(),
    val isDirty: Boolean = false,
)

sealed interface ReorderAccountsEffect {
    data object Saved : ReorderAccountsEffect
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
    ) : ReorderAccountsEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class ReorderAccountsViewModel(
    private val accountRepository: AccountRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val updateAccountDisplayOrderUseCase: UpdateAccountDisplayOrderUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReorderAccountsUiState())
    val uiState: StateFlow<ReorderAccountsUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<ReorderAccountsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var originalOrder: List<Long> = emptyList()
    private var originalItems: List<ReorderAccountItemUiModel> = emptyList()

    init {
        loadAccounts()
    }

    fun retryLoad() {
        loadAccounts()
    }

    private fun loadAccounts() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessageRes = null)
        viewModelScope.launch {
            try {
                val accounts = accountRepository.queryOpenAccounts().sortedBy { it.displayOrder }
                val balances = calculateAccountBalancesUseCase(accounts)
                val items = accounts.map {
                        ReorderAccountItemUiModel(
                            id = it.id,
                            name = it.name,
                            colorName = it.colorName,
                            iconName = it.iconName,
                            kind = it.kind,
                            balance = balances.getValue(it.id),
                            lastUsedAt = it.lastUsedAt,
                            isHidden = it.isHidden,
                        )
                    }
                originalOrder = items.map { it.id }
                originalItems = items
                _uiState.value = ReorderAccountsUiState(
                    isLoading = false,
                    accounts = items,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("ReorderAccountsViewModel", "Failed to load accounts", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessageRes = R.string.account_order_load_failed,
                )
            }
        }
    }

    fun moveAccountUp(accountId: Long) {
        moveByOne(accountId, -1)
    }

    fun moveAccountDown(accountId: Long) {
        moveByOne(accountId, 1)
    }

    private fun moveByOne(accountId: Long, direction: Int) {
        val accounts = _uiState.value.accounts
        val account = accounts.find { it.id == accountId } ?: return
        val group = accounts.filter { it.orderGroup == account.orderGroup }
        val index = group.indexOfFirst { it.id == accountId }
        group.getOrNull(index + direction)?.let { moveAccount(accountId, it.id) }
    }

    fun moveAccount(accountId: Long, targetId: Long) {
        updateOrder(moveAccountWithinGroup(_uiState.value.accounts, accountId, targetId))
    }

    private fun updateOrder(accounts: List<ReorderAccountItemUiModel>) {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            accounts = accounts,
            isDirty = accounts.map { it.id } != originalOrder,
        )
    }

    fun sortByBalance() {
        val sorted = sortAccountGroups(
            _uiState.value.accounts,
            compareByDescending<ReorderAccountItemUiModel> { it.balance }
                .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
        updateOrder(sorted)
    }

    fun undoChanges() {
        updateOrder(originalItems)
    }

    fun sortByRecentUse() {
        val sorted = sortAccountGroups(
            _uiState.value.accounts,
            compareByDescending<ReorderAccountItemUiModel> { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
        updateOrder(sorted)
    }

    fun sortByName() {
        updateOrder(sortAccountGroups(_uiState.value.accounts, compareBy { it.name }))
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || !state.isDirty) return
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                updateAccountDisplayOrderUseCase(
                    orderedAccountIds = state.accounts.map { it.id },
                )
                originalItems = state.accounts
                originalOrder = state.accounts.map { it.id }
                _uiState.value = state.copy(isDirty = false)
                effects.emit(ReorderAccountsEffect.Saved)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(ReorderAccountsEffect.ShowMessage(error.message.orEmpty(), messageRes = R.string.account_order_save_failed))
            }
        }
    }
}
