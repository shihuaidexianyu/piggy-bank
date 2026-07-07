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
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
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
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountDetailScreen(
            state = state,
            onManageAccount = { navController.navigate(MoneyDestination.editAccountRoute(accountId)) },
            onStartUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(accountId)) },
            onBackToAccounts = closeAccountsFlow,
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
                    archiveAccountUseCase = container.archiveAccountUseCase,
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
