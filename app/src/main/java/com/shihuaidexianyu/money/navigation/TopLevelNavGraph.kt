package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.shihuaidexianyu.money.MoneyAppContainer
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
import com.shihuaidexianyu.money.ui.stats.StatsScreen
import com.shihuaidexianyu.money.ui.stats.StatsViewModel
import com.shihuaidexianyu.money.ui.reminder.rememberNotificationPermissionGateway
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.di.SystemZoneIdProvider

private const val HISTORY_FILTER_REQUEST_KEY = "history_filter_request"

internal fun NavGraphBuilder.addTopLevelGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
    onBiometricLockChange: (Boolean) -> Unit,
) {
    composable(MoneyDestination.Home.route) {
        val entry = navController.currentBackStackEntry
        val batchReconcileMessage = entry
            ?.savedStateHandle
            ?.get<String>("batch_reconcile_message")
        val viewModel = viewModel<HomeViewModel>(
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                HomeViewModel(
                    observeHomeDashboardUseCase = container.observeHomeDashboardUseCase,
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
                    entry?.savedStateHandle?.remove<String>("batch_reconcile_message")
                },
                onStartUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
                onAllRemindersClick = { navController.navigate(MoneyDestination.ReminderListRoute) },
                onOpenSettings = { navController.navigate(MoneyDestination.Settings.route) },
                onManageAccounts = {
                    navController.navigate(MoneyDestination.Accounts.route) { launchSingleTop = true }
                },
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onRetry = viewModel::retry,
                onOpenMonthlyBudgetEditor = viewModel::openMonthlyBudgetEditor,
                onDismissMonthlyBudgetEditor = viewModel::dismissMonthlyBudgetEditor,
                onMonthlyBudgetInputChange = viewModel::updateMonthlyBudgetInput,
                onSaveMonthlyBudget = viewModel::saveMonthlyBudget,
                onRetryMonthlyBudgetSave = viewModel::retryMonthlyBudgetSave,
                onCloseMonthlyBudget = viewModel::closeMonthlyBudget,
                modifier = Modifier,
            )
    }

    composable(MoneyDestination.History.route) { entry ->
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
        val filterRequest by entry.savedStateHandle
            .getStateFlow<String?>(HISTORY_FILTER_REQUEST_KEY, null)
            .collectAsStateWithLifecycle()
        LaunchedEffect(filterRequest) {
            val encoded = filterRequest ?: return@LaunchedEffect
            runCatching { HistoryFilterNavigationRequest.decode(encoded) }
                .onSuccess(viewModel::applyExternalFilters)
            entry.savedStateHandle.remove<String>(HISTORY_FILTER_REQUEST_KEY)
        }
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
                onRecordClick = { record ->
                    when (record.kind) {
                        HistoryRecordKind.CASH_FLOW -> navController.navigate(MoneyDestination.editCashFlowRoute(record.recordId))
                        HistoryRecordKind.TRANSFER -> navController.navigate(MoneyDestination.editTransferRoute(record.recordId))
                        HistoryRecordKind.BALANCE_UPDATE -> navController.navigate(MoneyDestination.balanceUpdateDetailRoute(record.recordId))
                        HistoryRecordKind.BALANCE_ADJUSTMENT -> navController.navigate(MoneyDestination.balanceAdjustmentDetailRoute(record.recordId))
                    }
                },
            )
    }

    composable(MoneyDestination.Stats.route) {
        val viewModel = viewModel<StatsViewModel>(
            factory = moneyViewModelFactory {
                StatsViewModel(
                    observeStatsDashboardUseCase = container.observeStatsDashboardUseCase,
                    devicePreferencesRepository = container.devicePreferencesRepository,
                    clockProvider = SystemClockProvider,
                    zoneIdProvider = SystemZoneIdProvider,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        StatsScreen(
                state = state,
                onPreviousRange = viewModel::moveToPreviousRange,
                onNextRange = viewModel::moveToNextRange,
                onResetRange = viewModel::resetToCurrentRange,
                onOpenHistory = { filters ->
                    navController.navigate(MoneyDestination.History.route) { launchSingleTop = true }
                    navController.getBackStackEntry(MoneyDestination.History.route)
                        .savedStateHandle[HISTORY_FILTER_REQUEST_KEY] = HistoryFilterNavigationRequest.create(filters)
                },
                onRetry = viewModel::retry,
            )
    }

    composable(MoneyDestination.Accounts.route) {
        val viewModel = viewModel<AccountsViewModel>(
            factory = moneyViewModelFactory {
                AccountsViewModel(
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    accountRepository = container.accountRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    transactionRepository = container.transactionRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                    observeSavingsGoalUseCase = container.observeSavingsGoalUseCase,
                    observeAccountClosureIssuesUseCase = container.observeAccountClosureIssuesUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountsScreen(
                state = state,
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                onToggleClosedVisibility = viewModel::toggleClosedVisibility,
                onManageAccountOrder = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
                onManageSavingsGoal = { navController.navigate(MoneyDestination.SavingsGoalRoute) },
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
            onThemeModeChange = viewModel::updateThemeMode,
            onAmountColorModeChange = viewModel::updateAmountColorMode,
            onCurrencySymbolChange = viewModel::updateCurrencySymbol,
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
                navController.navigate(MoneyDestination.Accounts.route) { launchSingleTop = true }
            },
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
