package com.shihuaidexianyu.money.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountKind
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
    val loadErrorMessageRes: Int? = null,
    val name: String = "",
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
    val kind: AccountKind = AccountKind.DEFAULT,
    val isClosed: Boolean = false,
    val isHidden: Boolean = false,
    val currentBalance: Long = 0L,
    val isUpdatingHidden: Boolean = false,
    val reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
) {
    val canClose: Boolean get() = !isLoading && !isClosed && currentBalance == 0L
}

sealed interface EditAccountEffect {
    data object Saved : EditAccountEffect
    data object AccountClosed : EditAccountEffect
    data class HiddenChanged(val hidden: Boolean, val accountId: Long) : EditAccountEffect
    data object Closed : EditAccountEffect
    data class ShowMessage(
        override val message: String,
        @param:androidx.annotation.StringRes override val messageRes: Int? = null,
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
    private var baseline: EditAccountUiState? = null
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
                                        loadErrorMessageRes = R.string.account_load_failed,
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
                    loadErrorMessageRes = R.string.account_load_failed,
                )
            }
        }
    }

    fun retryLoad() {
        loadAccount()
        observeLifecycleAndBalance()
    }

    private fun loadAccount() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadErrorMessageRes = null)
        viewModelScope.launch {
            try {
                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    emitClosedOnce()
                    return@launch
                }
                val loaded = EditAccountUiState(
                    isLoading = false,
                    name = account.name,
                    colorName = account.colorName,
                    iconName = account.iconName,
                    kind = account.kind,
                    isClosed = account.isClosed,
                    isHidden = account.isHidden,
                    currentBalance = calculateCurrentBalanceUseCase(accountId),
                    reminderConfig = accountReminderSettingsRepository.getReminderConfig(accountId),
                )
                baseline = loaded
                _uiState.value = loaded
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("EditAccountViewModel", "Failed to load account", e) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorMessageRes = R.string.account_load_failed,
                )
            }
        }
    }

    fun updateName(value: String) = withDirty { copy(name = value.take(MAX_ACCOUNT_NAME_LENGTH)) }

    fun updateColorName(value: String) = withDirty { copy(colorName = normalizeAccountColorName(value)) }

    fun updateIconName(value: String) = withDirty { copy(iconName = normalizeAccountIconName(value)) }

    fun updateKind(value: AccountKind) = withDirty { copy(kind = value) }

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
                    effects.emit(EditAccountEffect.HiddenChanged(hidden, accountId))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isUpdatingHidden = false)
                    effects.emit(EditAccountEffect.ShowMessage(error.message.orEmpty(), messageRes = R.string.account_hidden_update_failed))
                }
        }
    }

    fun updateReminderPeriod(value: BalanceUpdateReminderPeriod) = withDirty {
        copy(reminderConfig = reminderConfig.copy(period = value))
    }

    fun updateReminderWeekday(value: BalanceUpdateReminderWeekday) = withDirty {
        copy(reminderConfig = reminderConfig.copy(weekday = value))
    }

    fun updateReminderMonthDay(value: Int) = withDirty {
        copy(reminderConfig = reminderConfig.copy(monthDay = value))
    }

    fun updateReminderTime(hour: Int, minute: Int) = withDirty {
        copy(reminderConfig = reminderConfig.copy(hour = hour, minute = minute))
    }

    /**
     * The enable switch persists immediately (like [setHidden]) instead of waiting for Save;
     * the NotificationSyncing repository wrapper triggers a notification sync on its own.
     * The baseline is kept in sync so an already-persisted toggle never reads as unsaved dirt.
     */
    fun setReminderEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (state.isClosed || state.isSaving || state.reminderConfig.isEnabled == enabled) return
        viewModelScope.launch {
            runCatching { accountReminderSettingsRepository.setEnabled(accountId, enabled) }
                .onSuccess {
                    baseline = baseline?.let { base ->
                        base.copy(reminderConfig = base.reminderConfig.copy(isEnabled = enabled))
                    }
                    val next = _uiState.value.copy(
                        reminderConfig = _uiState.value.reminderConfig.copy(isEnabled = enabled),
                    )
                    _uiState.value = next.copy(isDirty = computeIsDirty(next))
                }
                .onFailure { error ->
                    effects.emit(
                        EditAccountEffect.ShowMessage(
                            error.message.orEmpty(),
                            messageRes = R.string.msg_save_failed,
                        ),
                    )
                }
        }
    }

    private fun withDirty(transform: EditAccountUiState.() -> EditAccountUiState) {
        val next = _uiState.value.transform()
        _uiState.value = next.copy(isDirty = computeIsDirty(next))
    }

    private fun computeIsDirty(state: EditAccountUiState): Boolean {
        val base = baseline ?: return false
        return state.name != base.name ||
            state.colorName != base.colorName ||
            state.iconName != base.iconName ||
            state.kind != base.kind ||
            state.reminderConfig != base.reminderConfig
    }

    fun save() {
        val state = _uiState.value
        if (state.isClosed) {
            effects.tryEmit(EditAccountEffect.ShowMessage("", messageRes = R.string.account_closed_cannot_edit))
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
                    kind = state.kind,
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
                effects.emit(EditAccountEffect.ShowMessage(error.message.orEmpty(), messageRes = R.string.msg_save_failed))
            }
        }
    }

    fun closeAccount() {
        if (_uiState.value.isClosed) {
            effects.tryEmit(EditAccountEffect.ShowMessage("", messageRes = R.string.account_already_closed))
            return
        }
        viewModelScope.launch {
            val latestBalance = runCatching { calculateCurrentBalanceUseCase(accountId) }
                .getOrElse { error ->
                    effects.emit(EditAccountEffect.ShowMessage(error.message.orEmpty(), messageRes = R.string.account_read_latest_failed))
                    return@launch
                }
            _uiState.value = _uiState.value.copy(currentBalance = latestBalance)
            if (latestBalance != 0L) {
                effects.emit(EditAccountEffect.ShowMessage("", messageRes = R.string.account_close_balance_nonzero))
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
                val message = error.message.orEmpty()
                val messageRes = if (message.contains("余额必须为 0")) {
                    R.string.account_close_balance_nonzero
                } else {
                    R.string.account_close_failed
                }
                effects.emit(EditAccountEffect.ShowMessage(message, messageRes = messageRes))
            }
        }
    }

    private suspend fun emitClosedOnce() {
        if (closed) return
        closed = true
        effects.emit(EditAccountEffect.Closed)
    }
}
