package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.di.SystemZoneIdProvider
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.reminder.CreateReminderScreen
import com.shihuaidexianyu.money.ui.reminder.CreateReminderViewModel
import com.shihuaidexianyu.money.ui.reminder.EditReminderScreen
import com.shihuaidexianyu.money.ui.reminder.EditReminderViewModel
import com.shihuaidexianyu.money.ui.reminder.ReminderListScreen
import com.shihuaidexianyu.money.ui.reminder.ReminderListViewModel
import com.shihuaidexianyu.money.ui.reminder.ReminderUiModel
import com.shihuaidexianyu.money.ui.reminder.rememberNotificationPermissionGateway
import com.shihuaidexianyu.money.ui.reminder.shouldFinishPendingPermissionNavigation

internal fun NavGraphBuilder.addReminderGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    composable(MoneyDestination.ReminderListRoute) {
        val permissionGateway = rememberNotificationPermissionGateway(
            devicePreferencesRepository = container.devicePreferencesRepository,
            notificationSyncRequester = container.notificationSyncRequester,
        )
        val viewModel = viewModel<ReminderListViewModel>(
            factory = moneyViewModelFactory {
                ReminderListViewModel(
                    reminderRepository = container.recurringReminderRepository,
                    deleteReminderUseCase = container.deleteReminderUseCase,
                    skipReminderUseCase = container.skipReminderUseCase,
                    undoSkipReminderUseCase = container.undoSkipReminderUseCase,
                    observeHomeDashboardUseCase = container.observeHomeDashboardUseCase,
                    clockProvider = SystemClockProvider,
                    zoneIdProvider = SystemZoneIdProvider,
                    devicePreferencesRepository = container.devicePreferencesRepository,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        ReminderListScreen(
            state = state,
            onCreateReminder = { navController.navigate(MoneyDestination.CreateReminderRoute) },
            onEditReminder = { navController.navigate(MoneyDestination.editReminderRoute(it)) },
            onProcessReminder = { reminder ->
                navController.navigate(reminderCashFlowRoute(reminder))
            },
            onDeleteReminder = viewModel::deleteReminder,
            onSkipReminder = viewModel::skipReminder,
            onUndoSkip = viewModel::undoSkip,
            effects = viewModel.effectFlow,
            notificationPermissionState = permissionGateway.state,
            onRequestNotificationPermission = { permissionGateway.requestContextually() },
            onOpenNotificationSettings = permissionGateway.openSettings,
            onUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
            onBatchReconcile = { navController.navigate(MoneyDestination.BatchReconcileRoute) },
            onBack = { navController.popBackStack() },
        )
    }

    composable(MoneyDestination.CreateReminderRoute) {
        val permissionGateway = rememberNotificationPermissionGateway(
            devicePreferencesRepository = container.devicePreferencesRepository,
            notificationSyncRequester = container.notificationSyncRequester,
        )
        var finishAfterPermission by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(finishAfterPermission, permissionGateway.requestPending) {
            if (shouldFinishPendingPermissionNavigation(finishAfterPermission, permissionGateway.requestPending)) {
                finishAfterPermission = false
                navController.popBackStack()
            }
        }
        val viewModel = viewModel<CreateReminderViewModel>(
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                CreateReminderViewModel(
                    accountRepository = container.accountRepository,
                    createReminderUseCase = container.createReminderUseCase,
                    savedStateHandle = savedStateHandle,
                    clockProvider = SystemClockProvider,
                    zoneIdProvider = SystemZoneIdProvider,
                )
            },
        )
        CreateReminderScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onSaved = {
                if (permissionGateway.requestContextually()) {
                    finishAfterPermission = true
                } else {
                    navController.popBackStack()
                }
            },
        )
    }

    composable(
        route = MoneyDestination.EditReminderRoute,
        arguments = listOf(navArgument("reminderId") { type = NavType.LongType }),
    ) { entry ->
        val permissionGateway = rememberNotificationPermissionGateway(
            devicePreferencesRepository = container.devicePreferencesRepository,
            notificationSyncRequester = container.notificationSyncRequester,
        )
        var finishAfterPermission by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(finishAfterPermission, permissionGateway.requestPending) {
            if (shouldFinishPendingPermissionNavigation(finishAfterPermission, permissionGateway.requestPending)) {
                finishAfterPermission = false
                navController.popBackStack()
            }
        }
        val reminderId = entry.arguments?.getLong("reminderId") ?: return@composable
        val viewModel = viewModel<EditReminderViewModel>(
            key = "edit_reminder_$reminderId",
            factory = moneySavedStateViewModelFactory { savedStateHandle ->
                EditReminderViewModel(
                    reminderId = reminderId,
                    accountRepository = container.accountRepository,
                    reminderRepository = container.recurringReminderRepository,
                    updateReminderUseCase = container.updateReminderUseCase,
                    savedStateHandle = savedStateHandle,
                    zoneIdProvider = SystemZoneIdProvider,
                )
            },
        )
        EditReminderScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onSaved = { shouldRequest ->
                if (shouldRequest) {
                    if (permissionGateway.requestContextually()) {
                        finishAfterPermission = true
                    } else {
                        navController.popBackStack()
                    }
                } else {
                    navController.popBackStack()
                }
            },
        )
    }
}

internal fun reminderCashFlowRoute(reminder: ReminderUiModel): String =
    MoneyDestination.recordCashFlowRoute(
        direction = CashFlowDirection.fromValue(reminder.direction),
        accountId = reminder.accountId,
        amount = reminder.amount,
        note = reminder.name,
        reminderId = reminder.id,
        expectedDueAt = reminder.nextDueAt,
    )
