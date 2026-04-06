package com.shihuaidexianyu.money.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.ui.record.EditCashFlowScreen
import com.shihuaidexianyu.money.ui.record.EditCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.EditTransferScreen
import com.shihuaidexianyu.money.ui.record.EditTransferViewModel
import com.shihuaidexianyu.money.ui.record.RecordCashFlowScreen
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.RecordTransferScreen
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel

internal fun NavGraphBuilder.addRecordGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    val closeHistoryEditFlow = {
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

    composable(
        route = MoneyDestination.RecordCashFlowRoute + "?amount={amount}&purpose={purpose}&reminderId={reminderId}",
        arguments = listOf(
            navArgument("direction") { type = NavType.StringType },
            navArgument("accountId") { type = NavType.LongType },
            navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
            navArgument("purpose") { type = NavType.StringType; defaultValue = "" },
            navArgument("reminderId") { type = NavType.LongType; defaultValue = 0L },
        ),
    ) { entry ->
        val direction = CashFlowDirection.fromValue(entry.arguments?.getString("direction"))
        val accountId = entry.arguments?.getLong("accountId") ?: 0L
        val prefillAmount = entry.arguments?.getLong("amount") ?: 0L
        val prefillPurpose = entry.arguments?.getString("purpose") ?: ""
        val reminderId = entry.arguments?.getLong("reminderId") ?: 0L
        val viewModel = viewModel<RecordCashFlowViewModel>(
            key = "cash_flow_${direction.value}_${accountId}_$reminderId",
            factory = moneyViewModelFactory {
                RecordCashFlowViewModel(
                    direction = direction,
                    initialAccountId = accountId.takeIf { it > 0 },
                    prefillAmount = prefillAmount.takeIf { it > 0 },
                    prefillPurpose = prefillPurpose.takeIf { it.isNotEmpty() },
                    reminderId = reminderId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    createCashFlowRecordUseCase = container.createCashFlowRecordUseCase,
                    confirmReminderUseCase = container.confirmReminderUseCase,
                )
            },
        )
        RecordCashFlowScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.RecordTransferRoute,
        arguments = listOf(navArgument("fromAccountId") { type = NavType.LongType }),
    ) { entry ->
        val fromAccountId = entry.arguments?.getLong("fromAccountId") ?: 0L
        val viewModel = viewModel<RecordTransferViewModel>(
            key = "transfer_$fromAccountId",
            factory = moneyViewModelFactory {
                RecordTransferViewModel(
                    initialFromAccountId = fromAccountId.takeIf { it > 0 },
                    accountRepository = container.accountRepository,
                    createTransferRecordUseCase = container.createTransferRecordUseCase,
                )
            },
        )
        RecordTransferScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.EditCashFlowRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<EditCashFlowViewModel>(
            key = "edit_cash_$recordId",
            factory = moneyViewModelFactory {
                EditCashFlowViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    updateCashFlowRecordUseCase = container.updateCashFlowRecordUseCase,
                    deleteCashFlowRecordUseCase = container.deleteCashFlowRecordUseCase,
                )
            },
        )
        EditCashFlowScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDeleted = closeHistoryEditFlow,
        )
    }

    composable(
        route = MoneyDestination.EditTransferRoute,
        arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
    ) { entry ->
        val recordId = entry.arguments?.getLong("recordId") ?: return@composable
        val viewModel = viewModel<EditTransferViewModel>(
            key = "edit_transfer_$recordId",
            factory = moneyViewModelFactory {
                EditTransferViewModel(
                    recordId = recordId,
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    updateTransferRecordUseCase = container.updateTransferRecordUseCase,
                    deleteTransferRecordUseCase = container.deleteTransferRecordUseCase,
                )
            },
        )
        EditTransferScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onDeleted = closeHistoryEditFlow,
        )
    }
}
