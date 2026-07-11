package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
            factory = moneyViewModelFactory {
                HomeViewModel(
                    observeHomeDashboardUseCase = container.observeHomeDashboardUseCase,
                    devicePreferencesRepository = container.devicePreferencesRepository,
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
                onStartCashFlow = { direction ->
                    navController.navigate(MoneyDestination.recordCashFlowRoute(direction, accountId = 0L))
                },
                onStartTransfer = { navController.navigate(MoneyDestination.recordTransferRoute()) },
                onStartUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
                onAllRemindersClick = { navController.navigate(MoneyDestination.ReminderListRoute) },
                onOpenSettings = { navController.navigate(MoneyDestination.Settings.route) },
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onRetry = viewModel::retry,
                modifier = Modifier,
            )
    }

    composable(MoneyDestination.History.route) {
        val viewModel = viewModel<HistoryViewModel>(
            factory = moneyViewModelFactory {
                HistoryViewModel(
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    devicePreferencesRepository = container.devicePreferencesRepository,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HistoryScreen(
                state = state,
                onKeywordChange = viewModel::updateKeyword,
                onExcludeKeywordChange = viewModel::updateExcludeKeyword,
                onAccountChange = viewModel::updateAccount,
                onDateRangeChange = viewModel::updateDateRange,
                onMinAmountChange = viewModel::updateMinAmount,
                onMaxAmountChange = viewModel::updateMaxAmount,
                onAmountDirectionChange = viewModel::updateAmountDirectionFilter,
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
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        StatsScreen(
                state = state,
                onPeriodChange = viewModel::updatePeriod,
                onPreviousRange = viewModel::moveToPreviousRange,
                onNextRange = viewModel::moveToNextRange,
                onResetRange = viewModel::resetToCurrentRange,
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
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountsScreen(
                state = state,
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                onToggleClosedVisibility = viewModel::toggleClosedVisibility,
                onRetry = viewModel::retry,
            )
    }

    composable(MoneyDestination.Settings.route) {
        val viewModel = rememberSettingsViewModel(
            container = container,
        )
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
            onManageAccountOrder = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
            onCreateSavingsGoal = { navController.navigate(MoneyDestination.SavingsGoalRoute) },
            onExportData = viewModel::exportData,
            onImportData = viewModel::previewImport,
            onConfirmImport = viewModel::confirmImport,
            onRollbackImport = viewModel::rollbackImport,
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
