package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.SetAccountHiddenUseCase
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class EditAccountUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val name: String = "",
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
    val isClosed: Boolean = false,
    val isHidden: Boolean = false,
    val currentBalance: Long = 0L,
    val isUpdatingHidden: Boolean = false,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isSaving: Boolean = false,
) {
    val canClose: Boolean get() = !isLoading && !isClosed && currentBalance == 0L
}

sealed interface EditAccountEffect {
    data object Saved : EditAccountEffect
    data object AccountClosed : EditAccountEffect
    data object Closed : EditAccountEffect
    data class ShowMessage(
        override val message: String,
    ) : EditAccountEffect, com.shihuaidexianyu.money.ui.common.UiEffect.HasMessage
}

class EditAccountViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val closeAccountUseCase: CloseAccountUseCase,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val setAccountHiddenUseCase: SetAccountHiddenUseCase,
    private val transactionRepository: TransactionRepository,
    private val updateAccountUseCase: UpdateAccountUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    private val effects = MutableSharedFlow<EditAccountEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var closed = false
    private var lifecycleObservationJob: Job? = null

    init {
        loadAccount()
        observeLifecycleAndBalance()
    }

    private fun observeLifecycleAndBalance() {
        lifecycleObservationJob?.cancel()
        lifecycleObservationJob = viewModelScope.launch {
            try {
                combine(
                    accountRepository.observeAllAccounts(),
                    transactionRepository.observeChangeVersion(),
                ) { accounts, _ -> accounts.firstOrNull { it.id == accountId } }
                    .collect { account ->
                        if (account == null) {
                            emitClosedOnce()
                        } else {
                            runCatching { calculateCurrentBalanceUseCase(accountId) }
                                .onSuccess { balance ->
                                    _uiState.value = _uiState.value.copy(
                                        isClosed = account.isClosed,
                                        isHidden = account.isHidden,
                                        currentBalance = balance,
                                    )
                                }
                                .onFailure {
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        loadErrorMessage = "账户加载失败，请重试",
                                    )
                                }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditAccountViewModel", "Failed to observe account balance", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "账户加载失败，请重试",
                )
            }
        }
    }

    fun retryLoad() {
        loadAccount()
        observeLifecycleAndBalance()
    }

    private fun loadAccount() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessage = null)
        viewModelScope.launch {
            try {
                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    emitClosedOnce()
                    return@launch
                }
                _uiState.value = EditAccountUiState(
                    isLoading = false,
                    name = account.name,
                    colorName = account.colorName,
                    iconName = account.iconName,
                    isClosed = account.isClosed,
                    isHidden = account.isHidden,
                    currentBalance = calculateCurrentBalanceUseCase(accountId),
                    reminderConfig = accountReminderSettingsRepository.getReminderConfig(accountId),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditAccountViewModel", "Failed to load account", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessage = "账户加载失败，请重试",
                )
            }
        }
    }

    fun updateName(value: String) {
        _uiState.value = _uiState.value.copy(name = value.take(MAX_ACCOUNT_NAME_LENGTH))
    }

    fun updateColorName(value: String) {
        _uiState.value = _uiState.value.copy(colorName = normalizeAccountColorName(value))
    }

    fun updateIconName(value: String) {
        _uiState.value = _uiState.value.copy(iconName = normalizeAccountIconName(value))
    }

    fun setHidden(hidden: Boolean) {
        val state = _uiState.value
        if (state.isClosed || state.isUpdatingHidden || state.isHidden == hidden) return
        _uiState.value = state.copy(isUpdatingHidden = true)
        viewModelScope.launch {
            runCatching { setAccountHiddenUseCase(accountId, hidden) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isHidden = hidden,
                        isUpdatingHidden = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isUpdatingHidden = false)
                    effects.emit(EditAccountEffect.ShowMessage(error.message ?: "隐藏状态更新失败"))
                }
        }
    }

    fun updateReminderPeriod(value: BalanceUpdateReminderPeriod) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(period = value),
        )
    }

    fun updateReminderWeekday(value: BalanceUpdateReminderWeekday) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(weekday = value),
        )
    }

    fun updateReminderMonthDay(value: Int) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(monthDay = value),
        )
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(
            reminderConfig = _uiState.value.reminderConfig.copy(hour = hour, minute = minute),
        )
    }

    fun save() {
        val state = _uiState.value
        if (state.isClosed) {
            effects.tryEmit(EditAccountEffect.ShowMessage("关闭账户不能修改账户"))
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            runCatching {
                updateAccountUseCase(
                    accountId = accountId,
                    name = state.name,
                    balanceUpdateReminderConfig = state.reminderConfig,
                    colorName = state.colorName,
                    iconName = state.iconName,
                )
            }.onSuccess {
                effects.emit(EditAccountEffect.Saved)
            }.onFailure { error ->
                val lookup = runCatching { accountRepository.getAccountById(accountId) }
                if (lookup.isSuccess && lookup.getOrNull() == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                effects.emit(EditAccountEffect.ShowMessage(error.message ?: "保存失败"))
            }
        }
    }

    fun closeAccount() {
        if (_uiState.value.isClosed) {
            effects.tryEmit(EditAccountEffect.ShowMessage("账户已关闭"))
            return
        }
        viewModelScope.launch {
            val latestBalance = runCatching { calculateCurrentBalanceUseCase(accountId) }
                .getOrElse { error ->
                    effects.emit(EditAccountEffect.ShowMessage(error.message ?: "无法读取最新余额"))
                    return@launch
                }
            _uiState.value = _uiState.value.copy(currentBalance = latestBalance)
            if (latestBalance != 0L) {
                effects.emit(EditAccountEffect.ShowMessage("请先结清余额，再关闭账户"))
                return@launch
            }
            runCatching { closeAccountUseCase(accountId) }.onSuccess {
                effects.emit(EditAccountEffect.AccountClosed)
            }.onFailure { error ->
                val lookup = runCatching { accountRepository.getAccountById(accountId) }
                if (lookup.isSuccess && lookup.getOrNull() == null) {
                    emitClosedOnce()
                    return@onFailure
                }
                val message = if (error.message.orEmpty().contains("余额必须为 0")) {
                    "请先结清余额，再关闭账户"
                } else {
                    error.message ?: "关闭失败"
                }
                effects.emit(EditAccountEffect.ShowMessage(message))
            }
        }
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditAccountEffect.Closed)
    }
}
