package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.AccountDetailRecentRecord
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val loadErrorMessage: String? = null,
    val accountId: Long = 0,
    val name: String = "",
    val colorName: String = "blue",
    val iconName: String = "wallet",
    val isClosed: Boolean = false,
    val isReopening: Boolean = false,
    val currentBalance: Long = 0,
    val openAccountCount: Int = 0,
    val lastBalanceUpdateAt: Long? = null,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isStale: Boolean = false,
    val settings: PortableSettings = PortableSettings(),
    val monthInflow: Long = 0L,
    val monthOutflow: Long = 0L,
    val recentRecords: List<AccountDetailRecentRecord> = emptyList(),
)

fun AccountDetailUiState.canMutateLedger(): Boolean =
    !isLoading && !isMissing && loadErrorMessage == null && !isClosed

data class AccountClosurePresentation(
    val canMutate: Boolean,
    val canReopen: Boolean,
    val statusText: String,
)

fun accountClosurePresentation(isClosed: Boolean, balance: Long): AccountClosurePresentation = when {
    !isClosed -> AccountClosurePresentation(canMutate = true, canReopen = false, statusText = "开放")
    balance != 0L -> AccountClosurePresentation(
        canMutate = false,
        canReopen = true,
        statusText = "需重新开启并结清",
    )
    else -> AccountClosurePresentation(canMutate = false, canReopen = true, statusText = "已关闭")
}

sealed interface AccountDetailEffect {
    data class ShowMessage(
        override val message: String,
    ) : AccountDetailEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class AccountDetailViewModel(
    private val accountId: Long,
    private val observeAccountDetailUseCase: ObserveAccountDetailUseCase,
    private val reopenAccountUseCase: ReopenAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountDetailUiState(accountId = accountId))
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()
    private val effects = MutableSharedFlow<AccountDetailEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var observationJob: Job? = null

    init {
        observeDetail()
    }

    fun retry() {
        observeDetail()
    }

    fun reopenAccount() {
        val state = _uiState.value
        if (!state.isClosed || state.isReopening) return
        _uiState.value = state.copy(isReopening = true)
        viewModelScope.launch {
            runCatching { reopenAccountUseCase(accountId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isReopening = false)
                    effects.emit(AccountDetailEffect.ShowMessage("账户已重新开启，提醒仍保持关闭"))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isReopening = false)
                    effects.emit(AccountDetailEffect.ShowMessage(error.message ?: "重新开启失败"))
                }
        }
    }

    private fun observeDetail() {
        observationJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, isMissing = false, loadErrorMessage = null)
        observationJob = viewModelScope.launch {
            try {
                observeAccountDetailUseCase().collect { snapshot ->
                    val account = snapshot.account
                    _uiState.value = if (account == null) {
                        AccountDetailUiState(
                            isLoading = false,
                            isMissing = true,
                            isReopening = _uiState.value.isReopening,
                            accountId = accountId,
                            settings = snapshot.settings,
                        )
                    } else {
                        AccountDetailUiState(
                            isLoading = false,
                            accountId = account.id,
                            name = account.name,
                            colorName = account.colorName,
                            iconName = account.iconName,
                            isClosed = account.isClosed,
                            isReopening = _uiState.value.isReopening,
                            currentBalance = snapshot.currentBalance,
                            openAccountCount = snapshot.openAccountCount,
                            lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                            reminderConfig = snapshot.reminderConfig,
                            isStale = snapshot.isStale,
                            settings = snapshot.settings,
                            monthInflow = snapshot.monthInflow,
                            monthOutflow = snapshot.monthOutflow,
                            recentRecords = snapshot.recentRecords,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("AccountDetailViewModel", "Failed to observe account detail", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isMissing = false,
                    loadErrorMessage = "账户详情加载失败，请重试",
                )
            }
        }
    }
}
