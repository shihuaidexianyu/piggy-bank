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
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeViewModel
import com.shihuaidexianyu.money.ui.stats.StatsScreen
import com.shihuaidexianyu.money.ui.stats.StatsViewModel
import com.shihuaidexianyu.money.ui.settings.SettingsScreen

internal fun NavGraphBuilder.addTopLevelGraph(
    navController: NavHostController,
    container: MoneyAppContainer,
) {
    composable(MoneyDestination.Home.route) {
        val viewModel = viewModel<HomeViewModel>(
            factory = moneyViewModelFactory {
                HomeViewModel(
                    observeHomeDashboardUseCase = container.observeHomeDashboardUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            onStartCashFlow = { direction, accountId ->
                navController.navigate(MoneyDestination.recordCashFlowRoute(direction, accountId))
            },
            onStartTransfer = { navController.navigate(MoneyDestination.recordTransferRoute()) },
            onStartUpdateBalance = { navController.navigate(MoneyDestination.updateBalanceRoute(it)) },
            onReminderClick = { reminder ->
                val direction = CashFlowDirection.fromValue(reminder.direction)
                navController.navigate(
                    MoneyDestination.recordCashFlowRoute(
                        direction = direction,
                        accountId = reminder.accountId,
                        amount = reminder.amount,
                        purpose = reminder.name,
                        reminderId = reminder.id,
                    ),
                )
            },
            onAllRemindersClick = { navController.navigate(MoneyDestination.ReminderListRoute) },
            modifier = Modifier,
        )
    }

    composable(MoneyDestination.History.route) {
        val viewModel = viewModel<HistoryViewModel>(
            factory = moneyViewModelFactory {
                HistoryViewModel(
                    accountRepository = container.accountRepository,
                    transactionRepository = container.transactionRepository,
                    settingsRepository = container.settingsRepository,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        HistoryScreen(
            state = state,
            onKeywordChange = viewModel::updateKeyword,
            onAccountChange = viewModel::updateAccount,
            onDateRangeChange = viewModel::updateDateRange,
            onMinAmountChange = viewModel::updateMinAmount,
            onMaxAmountChange = viewModel::updateMaxAmount,
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
                    observeStatsUseCase = container.observeStatsUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        StatsScreen(
            state = state,
            onPeriodChange = viewModel::updatePeriod,
            onCashFlowModeChange = viewModel::updateCashFlowCardMode,
            onCashFlowGranularityChange = viewModel::updateCashFlowGranularity,
            onCashFlowDisplayUnitChange = viewModel::updateCashFlowDisplayUnit,
            onCashFlowDateSelect = viewModel::selectCashFlowDate,
            onCashFlowShiftPeriod = viewModel::shiftCashFlowVisiblePeriod,
            modifier = Modifier,
        )
    }

    composable(MoneyDestination.Accounts.route) {
        val viewModel = viewModel<AccountsViewModel>(
            factory = moneyViewModelFactory {
                AccountsViewModel(
                    accountReminderSettingsRepository = container.accountReminderSettingsRepository,
                    accountRepository = container.accountRepository,
                    settingsRepository = container.settingsRepository,
                    transactionRepository = container.transactionRepository,
                    calculateCurrentBalanceUseCase = container.calculateCurrentBalanceUseCase,
                )
            },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        AccountsScreen(
            state = state,
            onCreateAccount = { navController.navigate(MoneyDestination.CreateAccountRoute) },
            onAccountClick = { navController.navigate(MoneyDestination.accountDetailRoute(it)) },
            onToggleArchiveVisibility = viewModel::toggleArchiveVisibility,
        )
    }

    composable(MoneyDestination.Settings.route) {
        val viewModel = rememberSettingsViewModel(
            container = container,
            key = "settings_top_level",
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            onHomePeriodChange = viewModel::updateHomePeriod,
            onThemeModeChange = viewModel::updateThemeMode,
            onCurrencySymbolChange = viewModel::updateCurrencySymbol,
            onShowStaleMarkChange = viewModel::updateShowStaleMark,
            onManageAccountOrder = { navController.navigate(MoneyDestination.ReorderAccountsRoute) },
            onExportJson = viewModel::exportJson,
        )
    }
}
