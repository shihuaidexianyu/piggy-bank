package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.di.SystemZoneIdProvider
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.accounts.AccountsScreen
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import com.shihuaidexianyu.money.ui.settings.SettingsScreen
import com.shihuaidexianyu.money.ui.reminder.rememberNotificationPermissionGateway

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
                    devicePreferencesRepository = container.devicePreferencesRepository,
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
                onOpenHistory = {
                    navController.navigateToTopLevelTab(MoneyDestination.History)
                },
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
        HistoryScreenHost(navController = navController, container = container)
    }

    // Drill-down from account detail "查看全部": the same History surface with the account
    // scope fixed by the route — back returns to the detail page, and the tab's persisted
    // filters are neither read nor written here.
    composable(
        route = MoneyDestination.AccountHistoryRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: return@composable
        HistoryScreenHost(
            navController = navController,
            container = container,
            lockedAccountId = accountId,
            onBack = { navController.popBackStack() },
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
                    clockProvider = SystemClockProvider,
                    zoneIdProvider = SystemZoneIdProvider,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountsScreen(
                state = state,
                onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
                onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
                onToggleClosedVisibility = viewModel::toggleClosedVisibility,
                onReorderAccounts = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
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
            viewModel.refreshImportHistory()
            onPauseOrDispose { }
        }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            effectFlow = viewModel.effectFlow,
            onBack = { navController.popBackStack() },
            onThemeModeChange = viewModel::updateThemeMode,
            onAmountColorModeChange = viewModel::updateAmountColorMode,
            onCurrencySymbolChange = viewModel::updateCurrencySymbol,
            onBiometricLockChange = onBiometricLockChange,
            onRelockDelayChange = viewModel::updateRelockDelay,
            onMaskAmountsInAppChange = viewModel::updateMaskAmountsInApp,
            onHideNotificationAmountsChange = viewModel::updateHideNotificationAmounts,
            onHideRecentTasksChange = viewModel::updateHideRecentTasks,
            notificationPermissionState = notificationPermissionGateway.state,
            onRequestNotificationPermission = { notificationPermissionGateway.requestContextually() },
            onOpenNotificationSettings = notificationPermissionGateway.openSettings,
            onManageReminders = { navController.navigate(MoneyDestination.ReminderListRoute) },
            onManageAccountReminderConfigs = {
                navController.navigateToTopLevelTab(MoneyDestination.Accounts)
            },
            onExportData = viewModel::exportData,
            onImportData = viewModel::previewImport,
            onConfirmImport = viewModel::confirmImport,
            onRollbackImport = viewModel::rollbackImport,
        )
    }
}

/**
 * Shared wiring for the History tab and the account drill-down
 * (`history/account/{accountId}`). Both surfaces run the full History screen — search, filter
 * sheets and record editing — the drill-down just pins [lockedAccountId] into the
 * view model so the account scope, title, and back affordance come from the route.
 */
@Composable
private fun HistoryScreenHost(
    navController: NavHostController,
    container: MoneyAppContainer,
    lockedAccountId: Long? = null,
    onBack: (() -> Unit)? = null,
) {
    val viewModel = viewModel<HistoryViewModel>(
        key = lockedAccountId?.let { "account_history_$it" },
        factory = moneyViewModelFactory {
            HistoryViewModel(
                accountRepository = container.accountRepository,
                transactionRepository = container.transactionRepository,
                portableSettingsRepository = container.portableSettingsRepository,
                devicePreferencesRepository = container.devicePreferencesRepository,
                lockedAccountId = lockedAccountId,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val closedAccountReadOnlyMessage = stringResource(R.string.account_closed_readonly_description)
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
        lockedAccountId = lockedAccountId,
        onBack = onBack,
        // Quick-record from the drill-down pre-selects the scoped account; the tab keeps the
        // "ask me" default (0L).
        onRecordIncome = {
            navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    CashFlowDirection.INFLOW,
                    accountId = lockedAccountId ?: 0L,
                ),
            )
        },
        onRecordExpense = {
            navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    CashFlowDirection.OUTFLOW,
                    accountId = lockedAccountId ?: 0L,
                ),
            )
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
    )
}
