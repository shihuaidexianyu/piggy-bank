package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.domain.usecase.UuidLedgerOperationIdFactory
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateResultScreen
import com.shihuaidexianyu.money.ui.balance.BatchReconcileScreen
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateScreen
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateViewModel
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceScreen
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel

internal const val SupplementalEntrySavedTokenKey = "supplemental_entry_saved_token"

internal fun NavGraphBuilder.addBalanceGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    val closeBalanceUpdateFlow = {
        if (!navController.popBackStack()) {
            navController.navigate(MoneyDestination.History.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }
    val closeBalanceUpdateResult = { accountId: Long ->
        if (!navController.popBackStack(MoneyDestination.updateBalanceRoute(accountId), true)) {
            navController.navigate(MoneyDestination.Home.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    composable(
        route = MoneyDestination.BalanceUpdateDetailRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<BalanceUpdateDetailViewModel>(
            key = "balance_update_detail_$recordId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                BalanceUpdateDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    deleteBalanceUpdateRecordUseCase = container.deleteBalanceUpdateRecordUseCase,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        BalanceUpdateDetailScreen(
            viewModel = viewModel,
            state = state,
            settings = settingsState.portableSettings,
            onEdit = { navController.navigate(MoneyDestination.editBalanceUpdateRoute(recordId)) },
            onDeleted = closeBalanceUpdateFlow,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.EditBalanceUpdateRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<EditBalanceUpdateViewModel>(
            key = "edit_balance_update_$recordId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                EditBalanceUpdateViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    resolveBalanceUpdateContextUseCase = container.resolveBalanceUpdateContextUseCase,
                    updateBalanceUpdateRecordUseCase = container.updateBalanceUpdateRecordUseCase,
                    deleteBalanceUpdateRecordUseCase = container.deleteBalanceUpdateRecordUseCase,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        EditBalanceUpdateScreen(
            viewModel = viewModel,
            settings = settingsState.portableSettings,
            onBack = { navController.popBackStack() },
            onDeleted = closeBalanceUpdateFlow,
        )
    }

    composable(
        route = MoneyDestination.BalanceAdjustmentDetailRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<BalanceAdjustmentDetailViewModel>(
            key = "balance_adjustment_detail_$recordId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                BalanceAdjustmentDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    deleteBalanceAdjustmentUseCase = container.deleteBalanceAdjustmentUseCase,
                    savedStateHandle = savedStateHandle,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        BalanceAdjustmentDetailScreen(
            viewModel = viewModel,
            state = state,
            settings = settingsState.portableSettings,
            onClosed = closeBalanceUpdateFlow,
            onBack = { navController.popBackStack() },
        )
    }

    composable(MoneyDestination.BatchReconcileRoute) {
        val viewModel = viewModel<BatchReconcileViewModel>(
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                BatchReconcileViewModel(
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    accountRepository = container.accountRepository,
                    portableSettingsRepository = container.portableSettingsRepository,
                    transactionRepository = container.transactionRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                    savedStateHandle = savedStateHandle,
                    operationIdFactory = UuidLedgerOperationIdFactory,
                    clockProvider = SystemClockProvider,
                )
            },
        )
        BatchReconcileScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onSaved = { count ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("batch_reconcile_message", "已核对 $count 个账户")
                navController.popBackStack()
            },
        )
    }

    composable(
        route = MoneyDestination.UpdateBalanceRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: 0L
        val viewModel = viewModel<UpdateBalanceViewModel>(
            key = "update_balance_$accountId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                    savedStateHandle = savedStateHandle,
                    operationIdFactory = UuidLedgerOperationIdFactory,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        val supplementalToken by entry.savedStateHandle
            .getStateFlow(SupplementalEntrySavedTokenKey, 0L)
            .collectAsStateWithLifecycle()
        LaunchedEffect(supplementalToken) {
            if (supplementalToken != 0L) {
                viewModel.refreshLedgerBalanceAfterSupplementalEntry()
                entry.savedStateHandle.remove<Long>(SupplementalEntrySavedTokenKey)
            }
        }
        UpdateBalanceScreen(
            viewModel = viewModel,
            settings = settingsState.portableSettings,
            onShowResult = { navController.navigate(MoneyDestination.balanceUpdateResultRoute(accountId)) },
            onStartCashFlow = { direction, targetAccountId, amount ->
                navController.navigate(
                    MoneyDestination.recordCashFlowRoute(
                        direction = direction,
                        accountId = targetAccountId,
                        amount = amount,
                        note = "余额核对补记",
                        reminderId = null,
                        expectedDueAt = null,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.BalanceUpdateResultRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: return@composable
        val owner = navController.previousBackStackEntry
        if (owner?.destination?.route != MoneyDestination.UpdateBalanceRoute) {
            LaunchedEffect(accountId) {
                if (!navController.popBackStack()) {
                    navController.navigate(MoneyDestination.Home.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            }
            return@composable
        }
        val viewModel = viewModel<UpdateBalanceViewModel>(
            viewModelStoreOwner = owner,
            key = "update_balance_$accountId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                    savedStateHandle = savedStateHandle,
                    operationIdFactory = UuidLedgerOperationIdFactory,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        val updateState by viewModel.uiState.collectAsStateWithLifecycle()
        val result = updateState.latestResult
        if (result == null) {
            LaunchedEffect(accountId) {
                if (!navController.popBackStack()) {
                    navController.navigate(MoneyDestination.Home.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            }
            return@composable
        }
        BalanceUpdateResultScreen(
            result = result,
            settings = settingsState.portableSettings,
            onDone = { closeBalanceUpdateResult(accountId) },
            onOpenAccount = { targetAccountId ->
                closeBalanceUpdateResult(accountId)
                navController.navigate(MoneyDestination.accountDetailRoute(targetAccountId))
            },
        )
    }
}
