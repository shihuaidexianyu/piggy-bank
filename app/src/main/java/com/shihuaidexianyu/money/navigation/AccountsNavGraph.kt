package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.accounts.AccountDetailScreen
import com.shihuaidexianyu.money.ui.accounts.AccountDetailViewModel
import com.shihuaidexianyu.money.ui.accounts.CreateAccountScreen
import com.shihuaidexianyu.money.ui.accounts.CreateAccountViewModel
import com.shihuaidexianyu.money.ui.accounts.EditAccountScreen
import com.shihuaidexianyu.money.ui.accounts.EditAccountViewModel
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsScreen
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsViewModel
import com.shihuaidexianyu.money.ui.history.HistoryViewModel

internal fun NavGraphBuilder.addAccountsGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    val closeAccountsFlow = {
        if (!navController.popBackStack()) {
            navController.navigate(MoneyDestination.Accounts.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    composable(MoneyDestination.CreateAccountRoute) {
        val viewModel = viewModel<CreateAccountViewModel>(
            factory = moneyViewModelFactory {
                CreateAccountViewModel(container.createAccountUseCase)
            },
        )
        CreateAccountScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(MoneyDestination.ReorderAccountsRoute) {
        val viewModel = viewModel<ReorderAccountsViewModel>(
            factory = moneyViewModelFactory {
                ReorderAccountsViewModel(
                    accountRepository = container.accountRepository,
                    calculateAccountBalancesUseCase = container.calculateAccountBalancesUseCase,
                    updateAccountDisplayOrderUseCase = container.updateAccountDisplayOrderUseCase,
                )
            },
        )
        ReorderAccountsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.AccountDetailRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: return@composable
        val viewModel = viewModel<AccountDetailViewModel>(
            key = "account_detail_$accountId",
            factory = moneyViewModelFactory {
                AccountDetailViewModel(
                    accountId = accountId,
                    observeAccountDetailUseCase = container.observeAccountDetailUseCase(accountId),
                    reopenAccountUseCase = container.reopenAccountUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountDetailScreen(
            state = state,
            effectFlow = viewModel.effectFlow,
            onManageAccount = { navController.navigate(MoneyDestination.editAccountRoute(accountId)) },
            onReopenAccount = viewModel::reopenAccount,
            onBackToAccounts = closeAccountsFlow,
            onRetry = viewModel::retry,
            onViewAllHistory = {
                // Navigate FIRST: tab switching pops to the start destination, so the History
                // entry only exists on the back stack after the navigate — reading its entry
                // beforehand throws when the tab was never visited this session.
                navController.navigateToTopLevelTab(MoneyDestination.History)
                navController.currentBackStackEntry
                    ?.takeIf { it.destination.route == MoneyDestination.History.route }
                    ?.savedStateHandle
                    ?.set(HistoryViewModel.KEY_INITIAL_ACCOUNT_FILTER, accountId)
            },
        )
    }

    composable(
        route = MoneyDestination.EditAccountRoute,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
    ) { entry ->
        val accountId = entry.arguments?.getLong("accountId") ?: return@composable
        val viewModel = viewModel<EditAccountViewModel>(
            key = "edit_account_$accountId",
            factory = moneyViewModelFactory {
                EditAccountViewModel(
                    accountId = accountId,
                    accountRepository = container.accountRepository,
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    closeAccountUseCase = container.closeAccountUseCase,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                    setAccountHiddenUseCase = container.setAccountHiddenUseCase,
                    transactionRepository = container.transactionRepository,
                    updateAccountUseCase = container.updateAccountUseCase,
                )
            },
        )
        EditAccountScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onClosed = closeAccountsFlow,
        )
    }
}
