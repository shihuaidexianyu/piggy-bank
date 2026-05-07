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
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceAdjustmentDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailScreen
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailViewModel
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateResultScreen
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateScreen
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateViewModel
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceScreen
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel

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
            factory = moneyViewModelFactory {
                BalanceUpdateDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    deleteBalanceUpdateRecordUseCase = container.deleteBalanceUpdateRecordUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_update_detail",
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        BalanceUpdateDetailScreen(
            viewModel = viewModel,
            state = state,
            settings = settingsState.settings,
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
            factory = moneyViewModelFactory {
                EditBalanceUpdateViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    resolveBalanceUpdateContextUseCase = container.resolveBalanceUpdateContextUseCase,
                    updateBalanceUpdateRecordUseCase = container.updateBalanceUpdateRecordUseCase,
                    deleteBalanceUpdateRecordUseCase = container.deleteBalanceUpdateRecordUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_edit_balance_update",
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        EditBalanceUpdateScreen(
            viewModel = viewModel,
            settings = settingsState.settings,
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
            factory = moneyViewModelFactory {
                BalanceAdjustmentDetailViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_adjustment_detail",
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        BalanceAdjustmentDetailScreen(
            viewModel = viewModel,
            state = state,
            settings = settingsState.settings,
            onClosed = closeBalanceUpdateFlow,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.UpdateBalanceRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: 0L
        val viewModel = viewModel<UpdateBalanceViewModel>(
            key = "update_balance_$accountId",
            factory = moneyViewModelFactory {
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_update_balance",
        )
        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        UpdateBalanceScreen(
            viewModel = viewModel,
            settings = settingsState.settings,
            onShowResult = { navController.navigate(MoneyDestination.balanceUpdateResultRoute(accountId)) },
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
            factory = moneyViewModelFactory {
                UpdateBalanceViewModel(
                    initialAccountId = accountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    updateBalanceUseCase = container.updateBalanceUseCase,
                )
            },
        )
        val settingsViewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_for_balance_result",
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
            settings = settingsState.settings,
            onDone = { closeBalanceUpdateResult(accountId) },
            onOpenAccount = { targetAccountId ->
                closeBalanceUpdateResult(accountId)
                navController.navigate(MoneyDestination.accountDetailRoute(targetAccountId))
            },
        )
    }
}
