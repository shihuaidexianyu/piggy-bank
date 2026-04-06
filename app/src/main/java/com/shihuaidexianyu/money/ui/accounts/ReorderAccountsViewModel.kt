package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountOrderingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReorderAccountItemUiModel(
    val id: Long,
    val name: String,
    val groupType: AccountGroupType,
)

data class ReorderAccountsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val groupOrder: List<AccountGroupType> = AccountGroupType.entries,
    val accountsByGroup: Map<AccountGroupType, List<ReorderAccountItemUiModel>> = emptyMap(),
)

sealed interface ReorderAccountsEffect {
    data object Saved : ReorderAccountsEffect
    data class ShowMessage(
        override val message: String,
    ) : ReorderAccountsEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class ReorderAccountsViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val updateAccountOrderingUseCase: UpdateAccountOrderingUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReorderAccountsUiState())
    val uiState: StateFlow<ReorderAccountsUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<ReorderAccountsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.observeSettings().first()
                val groupOrder = settings.accountGroupOrder
                val accountsByGroup = accountRepository.queryActiveAccounts()
                    .groupBy { AccountGroupType.fromValue(it.groupType) }
                    .mapValues { (_, accounts) ->
                        accounts.sortedBy { it.displayOrder }.map {
                            ReorderAccountItemUiModel(
                                id = it.id,
                                name = it.name,
                                groupType = AccountGroupType.fromValue(it.groupType),
                            )
                        }
                    }
                _uiState.value = ReorderAccountsUiState(
                    isLoading = false,
                    groupOrder = groupOrder,
                    accountsByGroup = groupOrder.associateWith { group -> accountsByGroup[group].orEmpty() },
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun moveGroupUp(groupType: AccountGroupType) {
        val groups = _uiState.value.groupOrder.toMutableList()
        val index = groups.indexOf(groupType)
        if (index <= 0) return
        val item = groups.removeAt(index)
        groups.add(index - 1, item)
        _uiState.value = _uiState.value.copy(groupOrder = groups)
    }

    fun moveGroupDown(groupType: AccountGroupType) {
        val groups = _uiState.value.groupOrder.toMutableList()
        val index = groups.indexOf(groupType)
        if (index < 0 || index >= groups.lastIndex) return
        val item = groups.removeAt(index)
        groups.add(index + 1, item)
        _uiState.value = _uiState.value.copy(groupOrder = groups)
    }

    fun moveAccountUp(groupType: AccountGroupType, accountId: Long) {
        val items = _uiState.value.accountsByGroup[groupType].orEmpty().toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index <= 0) return
        val item = items.removeAt(index)
        items.add(index - 1, item)
        _uiState.value = _uiState.value.copy(
            accountsByGroup = _uiState.value.accountsByGroup + (groupType to items),
        )
    }

    fun moveAccountDown(groupType: AccountGroupType, accountId: Long) {
        val items = _uiState.value.accountsByGroup[groupType].orEmpty().toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index < 0 || index >= items.lastIndex) return
        val item = items.removeAt(index)
        items.add(index + 1, item)
        _uiState.value = _uiState.value.copy(
            accountsByGroup = _uiState.value.accountsByGroup + (groupType to items),
        )
    }

    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            runCatching {
                val state = _uiState.value
                updateAccountOrderingUseCase(
                    groupOrder = state.groupOrder,
                    orderedAccountIds = state.groupOrder.flatMap { group ->
                        state.accountsByGroup[group].orEmpty().map { it.id }
                    },
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
