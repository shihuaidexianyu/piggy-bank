package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
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
    val balance: Long,
    val lastUsedAt: Long?,
)

data class ReorderAccountsUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val accounts: List<ReorderAccountItemUiModel> = emptyList(),
)

sealed interface ReorderAccountsEffect {
    data object Saved : ReorderAccountsEffect
    data class ShowMessage(
        override val message: String,
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

    init {
        loadAccounts()
    }

    fun retryLoad() {
        loadAccounts()
    }

    private fun loadAccounts() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
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
                            balance = balances.getValue(it.id),
                            lastUsedAt = it.lastUsedAt,
                        )
                    }
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
                    loadErrorMessage = "账户顺序加载失败，请重试",
                )
            }
        }
    }

    fun moveAccountUp(accountId: Long) {
        val items = _uiState.value.accounts.toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index <= 0) return
        val item = items.removeAt(index)
        items.add(index - 1, item)
        _uiState.value = _uiState.value.copy(accounts = items)
    }

    fun moveAccountDown(accountId: Long) {
        val items = _uiState.value.accounts.toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index < 0 || index >= items.lastIndex) return
        val item = items.removeAt(index)
        items.add(index + 1, item)
        _uiState.value = _uiState.value.copy(accounts = items)
    }

    fun sortByBalance() {
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.sortedWith(
                compareByDescending<ReorderAccountItemUiModel> { it.balance }
                    .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
                    .thenBy { it.name },
            ),
        )
    }

    fun sortByRecentUse() {
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.sortedWith(
                compareByDescending<ReorderAccountItemUiModel> { it.lastUsedAt ?: Long.MIN_VALUE }
                    .thenBy { it.name },
            ),
        )
    }

    fun sortByName() {
        _uiState.value = _uiState.value.copy(
            accounts = _uiState.value.accounts.sortedBy { it.name },
        )
    }

    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            runCatching {
                val state = _uiState.value
                updateAccountDisplayOrderUseCase(
                    orderedAccountIds = state.accounts.map { it.id },
                )
            }.onSuccess {
                effects.emit(ReorderAccountsEffect.Saved)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(ReorderAccountsEffect.ShowMessage(error.message ?: "保存排序失败"))
            }
        }
    }
}
