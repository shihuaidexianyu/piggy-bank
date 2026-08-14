package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.settings.SavingsGoalScreen
import com.shihuaidexianyu.money.ui.settings.SavingsGoalViewModel
import com.shihuaidexianyu.money.ui.reminder.rememberNotificationPermissionGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun NavGraphBuilder.addTopLevelGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
    onBiometricLockChange: (Boolean) -> Unit,
) {
    composable(MoneyDestination.Home.route) { homeEntry ->
        // Read from THIS destination's entry — navController.currentBackStackEntry points at
        // whichever screen is on top during transitions/recompositions, so reading (and worse,
        // clearing) the message there could hit the wrong SavedStateHandle.
        val batchReconcileMessage = homeEntry
            .savedStateHandle
            .get<String>("batch_reconcile_message")
        val viewModel = viewModel<HomeViewModel>(
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                HomeViewModel(
                    observeHomeDashboardUseCase = container.observeHomeDashboardUseCase,
                    observeSavingsGoalUseCase = container.observeSavingsGoalUseCase,
                    devicePreferencesRepository = container.devicePreferencesRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HomeScreen(
                state = state,
                snackbarMessage = batchReconcileMessage,
                onSnackbarMessageShown = {
                    homeEntry.savedStateHandle.remove<String>("batch_reconcile_message")
                },
                onStartUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
                onAllRemindersClick = { navController.navigate(MoneyDestination.ReminderListRoute) },
                onOpenSettings = {
                    navController.navigate(MoneyDestination.Settings.route) { launchSingleTop = true }
                },
                onManageAccounts = {
                    navController.navigateToTopLevelTab(MoneyDestination.Accounts)
                },
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onRetry = viewModel::retry,
                onSelectPeriod = viewModel::selectPeriod,
                onOpenMonthlyBudgetEditor = viewModel::openMonthlyBudgetEditor,
                onDismissMonthlyBudgetEditor = viewModel::dismissMonthlyBudgetEditor,
                onMonthlyBudgetInputChange = viewModel::updateMonthlyBudgetInput,
                onSaveMonthlyBudget = viewModel::saveMonthlyBudget,
                onRetryMonthlyBudgetSave = viewModel::retryMonthlyBudgetSave,
                onCloseMonthlyBudget = viewModel::closeMonthlyBudget,
                onOpenHistory = {
                    navController.navigateToTopLevelTab(MoneyDestination.History)
                },
                onOpenSavingsGoal = { navController.navigate(MoneyDestination.SavingsGoalRoute) },
                onOpenRecord = { record ->
                    when (record.kind) {
                        HistoryRecordKind.CASH_FLOW -> navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                        HistoryRecordKind.TRANSFER -> navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                        HistoryRecordKind.BALANCE_UPDATE -> navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                        HistoryRecordKind.BALANCE_ADJUSTMENT -> navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                    }
                },
                modifier = Modifier,
            )
    }

    composable(MoneyDestination.History.route) {
        val devicePreferences by container.devicePreferencesRepository.observe()
            .collectAsStateWithLifecycle(initialValue = DevicePreferences())
        val viewModel = viewModel<HistoryViewModel>(
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                HistoryViewModel(
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    devicePreferencesRepository = container.devicePreferencesRepository,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
        val deletedMessage = stringResource(R.string.ledger_record_deleted)
        val deleteFailedMessage = stringResource(R.string.ledger_record_delete_failed)
        val closedAccountReadOnlyMessage = stringResource(R.string.account_closed_readonly_description)
        val undoLabel = stringResource(R.string.action_undo)
        val deletingRecordIds = remember { mutableSetOf<String>() }
        HistoryScreen(
                state = state,
                onKeywordChange = viewModel::updateKeyword,
                onExcludeKeywordChange = viewModel::updateExcludeKeyword,
                onRecordTypesChange = viewModel::updateRecordTypes,
                onAccountChange = viewModel::updateAccount,
                onDateRangeChange = viewModel::updateDateRange,
                onMinAmountChange = viewModel::updateMinAmount,
                onMaxAmountChange = viewModel::updateMaxAmount,
                onAmountDirectionChange = viewModel::updateAmountDirectionFilter,
                onClearAllFilters = viewModel::clearFilters,
                onLoadMore = viewModel::loadMore,
                onRetryLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
                onRecordIncome = {
                    navController.navigate(MoneyDestination.recordCashFlowRoute(CashFlowDirection.INFLOW, accountId = 0L))
                },
                onRecordExpense = {
                    navController.navigate(MoneyDestination.recordCashFlowRoute(CashFlowDirection.OUTFLOW, accountId = 0L))
                },
                onRecordClick = { record ->
                    if (!record.canMutate) {
                        rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(closedAccountReadOnlyMessage))
                    } else {
                        when (record.kind) {
                            HistoryRecordKind.CASH_FLOW -> navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                            HistoryRecordKind.TRANSFER -> navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                            HistoryRecordKind.BALANCE_UPDATE -> navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                            HistoryRecordKind.BALANCE_ADJUSTMENT -> navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                        }
                    }
                },
                historySwipeDeleteEnabled = devicePreferences.historySwipeDeleteEnabled,
                historySwipeEditEnabled = devicePreferences.historySwipeEditEnabled,
                onDeleteRecord = delete@{ record ->
                    if (!record.canMutate || !deletingRecordIds.add(record.id)) return@delete
                    scope.launch {
                        try {
                            val undoToken = when (record.kind) {
                                HistoryRecordKind.CASH_FLOW ->
                                    container.deleteCashFlowRecordUseCase(record.recordId)
                                HistoryRecordKind.TRANSFER ->
                                    container.deleteTransferRecordUseCase(record.recordId)
                                HistoryRecordKind.BALANCE_UPDATE ->
                                    container.deleteBalanceUpdateRecordUseCase(record.recordId)
                                HistoryRecordKind.BALANCE_ADJUSTMENT ->
                                    container.deleteBalanceAdjustmentUseCase(record.recordId)
                            }
                            if (undoToken != null) {
                                rootSnackbarDispatcher?.dispatch(
                                    rootSnackbarEffect(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        action = RootSnackbarAction.RestoreLedger(undoToken),
                                    ),
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            rootSnackbarDispatcher?.dispatch(
                                rootSnackbarEffect(error.message ?: deleteFailedMessage),
                            )
                        } finally {
                            deletingRecordIds.remove(record.id)
                        }
                    }
                },
                onEditRecord = { record ->
                    if (!record.canMutate) {
                        rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(closedAccountReadOnlyMessage))
                    } else {
                        when (record.kind) {
                            HistoryRecordKind.CASH_FLOW ->
                                navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                            HistoryRecordKind.TRANSFER ->
                                navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                            HistoryRecordKind.BALANCE_UPDATE ->
                                navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                            HistoryRecordKind.BALANCE_ADJUSTMENT ->
                                navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                        }
                    }
                },
            )
    }

    composable(MoneyDestination.Accounts.route) {
        val devicePreferences by container.devicePreferencesRepository.observe()
            .collectAsStateWithLifecycle(initialValue = DevicePreferences())
        val viewModel = viewModel<AccountsViewModel>(
            factory = moneyViewModelFactory {
                AccountsViewModel(
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    accountRepository = container.accountRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    transactionRepository = container.transactionRepository,
                    savingsGoalRepository = container.savingsGoalRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountsScreen(
                state = state,
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                onToggleClosedVisibility = viewModel::toggleClosedVisibility,
                onManageSavingsGoal = { navController.navigate(MoneyDestination.SavingsGoalRoute) },
                onReorderAccounts = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
                accountSwipeReconcileEnabled = devicePreferences.accountSwipeReconcileEnabled,
                onReconcileAccount = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
                onRetry = viewModel::retry,
            )
    }

    composable(MoneyDestination.Settings.route) {
        val notificationPermissionGateway = rememberNotificationPermissionGateway(
            devicePreferencesRepository = container.devicePreferencesRepository,
            notificationSyncRequester = container.notificationSyncRequester,
        )
        val viewModel = rememberSettingsViewModel(
            container = container,
        )
        // SettingsViewModel is Activity-scoped; recompute the current canonical content hash on
        // every Settings entry so rollback eligibility cannot survive an intervening mutation.
        LifecycleResumeEffect(viewModel) {
            viewModel.retryImportHistory()
            onPauseOrDispose { }
        }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            effectFlow = viewModel.effectFlow,
            onBack = { navController.popBackStack() },
            onThemeModeChange = viewModel::updateThemeMode,
            onUseDynamicColorChange = viewModel::updateUseDynamicColor,
            onAmountColorModeChange = viewModel::updateAmountColorMode,
            onCurrencySymbolChange = viewModel::updateCurrencySymbol,
            onHistorySwipeDeleteChange = viewModel::updateHistorySwipeDeleteEnabled,
            onHistorySwipeEditChange = viewModel::updateHistorySwipeEditEnabled,
            onAccountSwipeReconcileChange = viewModel::updateAccountSwipeReconcileEnabled,
            onBiometricLockChange = onBiometricLockChange,
            onRelockDelayChange = viewModel::updateRelockDelay,
            onMaskAmountsInAppChange = viewModel::updateMaskAmountsInApp,
            onHideWidgetAmountsChange = viewModel::updateHideWidgetAmounts,
            onHideNotificationAmountsChange = viewModel::updateHideNotificationAmounts,
            onHideRecentTasksChange = viewModel::updateHideRecentTasks,
            notificationPermissionState = notificationPermissionGateway.state,
            recurringNotificationChannelEnabled = notificationPermissionGateway.recurringChannelEnabled,
            balanceNotificationChannelEnabled = notificationPermissionGateway.balanceChannelEnabled,
            onRequestNotificationPermission = { notificationPermissionGateway.requestContextually() },
            onOpenNotificationSettings = notificationPermissionGateway.openSettings,
            onManageReminders = { navController.navigate(MoneyDestination.ReminderListRoute) },
            onManageAccountReminderConfigs = {
                navController.navigateToTopLevelTab(MoneyDestination.Accounts)
            },
            onManageAccountOrder = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
            onExportData = viewModel::exportData,
            onImportData = viewModel::previewImport,
            onConfirmImport = viewModel::confirmImport,
            onRollbackImport = viewModel::rollbackImport,
            onRetryImportHistory = viewModel::retryImportHistory,
        )
    }

    composable(MoneyDestination.SavingsGoalRoute) {
        val viewModel = viewModel<SavingsGoalViewModel>(
            factory = moneyViewModelFactory {
                SavingsGoalViewModel(
                    savingsGoalRepository = container.savingsGoalRepository,
                    observeSavingsGoalUseCase = container.observeSavingsGoalUseCase,
                    upsertSavingsGoalUseCase = container.upsertSavingsGoalUseCase,
                    clearSavingsGoalUseCase = container.clearSavingsGoalUseCase,
                )
            },
        )
        SavingsGoalScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }
}
