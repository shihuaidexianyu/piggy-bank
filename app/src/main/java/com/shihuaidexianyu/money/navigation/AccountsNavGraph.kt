package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
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
        val availabilityFlow = remember(container) {
            openAccountAvailability(container.accountRepository.observeOpenAccounts())
        }
        val availability by availabilityFlow.collectAsStateWithLifecycle(initialValue = OpenAccountAvailability.Loading)
        val rootDispatcher = LocalRootSnackbarDispatcher.current
        val needSecondAccountMessage = stringResource(R.string.ledger_fab_need_second_message)
        val createAccountLabel = stringResource(R.string.accounts_create)
        AccountDetailScreen(
            state = state,
            effectFlow = viewModel.effectFlow,
            onManageAccount = { navController.navigate(MoneyDestination.editAccountRoute(accountId)) },
            onReconcileAccount = { navController.navigate(MoneyDestination.updateBalanceRoute(accountId)) },
            onRecordExpense = {
                navController.navigate(MoneyDestination.recordCashFlowRoute(com.shihuaidexianyu.money.domain.model.CashFlowDirection.OUTFLOW, accountId))
            },
            onRecordIncome = {
                navController.navigate(MoneyDestination.recordCashFlowRoute(com.shihuaidexianyu.money.domain.model.CashFlowDirection.INFLOW, accountId))
            },
            transferEnabled = availability is OpenAccountAvailability.Data,
            onTransfer = {
                val current = availability as? OpenAccountAvailability.Data
                if (current != null) {
                    when (resolveLedgerFabAction(LedgerFabAction.TRANSFER, current)) {
                        LedgerFabDecision.OpenTransferForm -> navController.navigate(MoneyDestination.recordTransferRoute(accountId))
                        else -> rootDispatcher?.dispatch(
                            rootSnackbarEffect(needSecondAccountMessage, createAccountLabel, RootSnackbarAction.CreateAccount),
                        )
                    }
                }
            },
            onReopenAccount = viewModel::reopenAccount,
            onBackToAccounts = closeAccountsFlow,
            onRetry = viewModel::retry,
            onViewAllHistory = {
                // Drill-down (not a tab switch): the account scope rides the route, so back
                // returns here and the History tab's own filters stay untouched.
                navController.navigate(MoneyDestination.accountHistoryRoute(accountId))
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
