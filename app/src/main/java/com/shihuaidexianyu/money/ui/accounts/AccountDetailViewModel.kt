package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.usecase.InvestmentSettlementSummary
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val accountId: Long = 0,
    val name: String = "",
    val groupType: AccountGroupType = AccountGroupType.PAYMENT,
    val currentBalance: Long = 0,
    val lastBalanceUpdateAt: Long? = null,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isStale: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val latestSettlement: InvestmentSettlementSummary? = null,
)

class AccountDetailViewModel(
    private val accountId: Long,
    private val observeAccountDetailUseCase: ObserveAccountDetailUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountDetailUiState(accountId = accountId))
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                observeAccountDetailUseCase().collect { snapshot ->
                    val account = snapshot.account
                    _uiState.value = if (account == null) {
                        AccountDetailUiState(
                            isLoading = false,
                            isMissing = true,
                            accountId = accountId,
                            settings = snapshot.settings,
                        )
                    } else {
                        AccountDetailUiState(
                            isLoading = false,
                            accountId = account.id,
                            name = account.name,
                            groupType = AccountGroupType.fromValue(account.groupType),
                            currentBalance = snapshot.currentBalance,
                            lastBalanceUpdateAt = account.lastBalanceUpdateAt,
                            reminderConfig = snapshot.reminderConfig,
                            isStale = snapshot.isStale,
                            settings = snapshot.settings,
                            latestSettlement = snapshot.latestSettlement,
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, isMissing = true)
            }
        }
    }
}

