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
import com.shihuaidexianyu.money.ui.reminder.CreateReminderScreen
import com.shihuaidexianyu.money.ui.reminder.CreateReminderViewModel
import com.shihuaidexianyu.money.ui.reminder.EditReminderScreen
import com.shihuaidexianyu.money.ui.reminder.EditReminderViewModel
import com.shihuaidexianyu.money.ui.reminder.ReminderListScreen
import com.shihuaidexianyu.money.ui.reminder.ReminderListViewModel

internal fun NavGraphBuilder.addReminderGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    composable(MoneyDestination.ReminderListRoute) {
        val viewModel = viewModel<ReminderListViewModel>(
            factory = moneyViewModelFactory {
                ReminderListViewModel(
                    reminderRepository = container.recurringReminderRepository,
                    settingsRepository = container.settingsRepository,
                    deleteReminderUseCase = container.deleteReminderUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ReminderListScreen(
            state = state,
            onCreateReminder = { navController.navigate(MoneyDestination.CreateReminderRoute) },
            onEditReminder = { navController.navigate(MoneyDestination.editReminderRoute(it)) },
            onDeleteReminder = viewModel::deleteReminder,
            onBack = { navController.popBackStack() },
        )
    }

    composable(MoneyDestination.CreateReminderRoute) {
        val viewModel = viewModel<CreateReminderViewModel>(
            factory = moneyViewModelFactory {
                CreateReminderViewModel(
                    accountRepository = container.accountRepository,
                    createReminderUseCase = container.createReminderUseCase,
                )
            },
        )
        CreateReminderScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = MoneyDestination.EditReminderRoute,
        arguments = listOf(navArgument("reminderId") { type = NavType.LongType }),
    ) { entry ->
        val reminderId = entry.arguments?.getLong("reminderId") ?: return@composable
        val viewModel = viewModel<EditReminderViewModel>(
            key = "edit_reminder_$reminderId",
            factory = moneyViewModelFactory {
                EditReminderViewModel(
                    reminderId = reminderId,
                    accountRepository = container.accountRepository,
                    reminderRepository = container.recurringReminderRepository,
                    updateReminderUseCase = container.updateReminderUseCase,
                )
            },
        )
        EditReminderScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }
}
