package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.SavingsGoalProgress
import com.shihuaidexianyu.money.domain.usecase.MonthlyBudgetStatus
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartUpdateBalance: (Long) -> Unit,
    onAllRemindersClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarMessage: String? = null,
    onSnackbarMessageShown: () -> Unit = {},
    onManageAccounts: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenMonthlyBudgetEditor: () -> Unit = {},
    onDismissMonthlyBudgetEditor: () -> Unit = {},
    onMonthlyBudgetInputChange: (String) -> Unit = {},
    onSaveMonthlyBudget: () -> Unit = {},
    onRetryMonthlyBudgetSave: () -> Unit = {},
    onCloseMonthlyBudget: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSavingsGoal: () -> Unit = {},
    onOpenRecord: (HomeRecentRecordUiModel) -> Unit = {},
) {
    var showUpdateBalancePicker by remember { mutableStateOf(false) }
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val homeLoadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(it, token = "home:$it"))
            onSnackbarMessageShown()
        }
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = stringResource(R.string.home_choose_reconcile_account),
            accounts = state.accountOptions,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }
    if (state.showMonthlyBudgetEditor) {
        MonthlyBudgetEditorDialog(
            input = state.monthlyBudgetInput,
            inputErrorRes = state.monthlyBudgetInputErrorRes,
            saveErrorRes = state.monthlyBudgetSaveErrorRes,
            isSaving = state.isMonthlyBudgetSaving,
            hasBudget = state.monthlyBudget != null,
            onInputChange = onMonthlyBudgetInputChange,
            onSave = if (state.monthlyBudgetSaveErrorRes != null) {
                onRetryMonthlyBudgetSave
            } else {
                onSaveMonthlyBudget
            },
            onCloseBudget = onCloseMonthlyBudget,
            onDismiss = onDismissMonthlyBudgetEditor,
        )
    }
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = stringResource(R.string.home_title),
            trailing = {
                HomeHeaderActions(
                    dueCount = state.dueReminders.size + state.staleAccountCount,
                    onOpenSettings = onOpenSettings,
                    onOpenReminders = onAllRemindersClick,
                )
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        AsyncContentRenderer(
            content = state.toAsyncContent(homeLoadErrorMessage),
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize(),
            empty = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MoneyEmptyStateCard(
                        title = stringResource(R.string.home_create_first_account),
                        subtitle = stringResource(R.string.home_create_first_account_description),
                    ) {
                        OutlinedButton(onClick = onCreateAccount) {
                            Text(stringResource(R.string.home_create_now))
                        }
                    }
                }
            },
            data = { renderedState, _ ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = MoneyDimens.screenHorizontalPadding,
                        top = 8.dp,
                        end = MoneyDimens.screenHorizontalPadding,
                        bottom = MoneyDimens.bottomNavContentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        PeriodOverviewBlock(
                            totalAssets = renderedState.totalAssets,
                            cashInflow = renderedState.periodCashInflow,
                            cashOutflow = renderedState.periodCashOutflow,
                            settings = renderedState.settings,
                        )
                    }
                    if (renderedState.accountOptions.isEmpty()) {
                        item {
                            HomeOpenAccountCta(onManageAccounts = onManageAccounts)
                        }
                    }
                    item {
                        MonthlyBudgetBlock(
                            budget = renderedState.monthlyBudget,
                            settings = renderedState.settings,
                            onEdit = onOpenMonthlyBudgetEditor,
                            onClose = onCloseMonthlyBudget,
                        )
                    }
                    renderedState.savingsGoalProgress?.let { savingsGoalProgress ->
                        item {
                            HomeSavingsGoalBlock(
                                progress = savingsGoalProgress,
                                settings = renderedState.settings,
                                onOpenSavingsGoal = onOpenSavingsGoal,
                            )
                        }
                    }
                    if (renderedState.dueReminders.isNotEmpty()) {
                        item {
                            HomeReminderSection(
                                reminders = renderedState.dueReminders,
                                onOpenReminders = onAllRemindersClick,
                            )
                        }
                    }
                    if (renderedState.staleAccounts.isNotEmpty()) {
                        item {
                            HomeStaleAccountSection(
                                accounts = renderedState.staleAccounts,
                                settings = renderedState.settings,
                                onReconcile = onStartUpdateBalance,
                                onChooseAccount = { showUpdateBalancePicker = true },
                                showReconcileAction = renderedState.accountOptions.isNotEmpty(),
                            )
                        }
                    }
                    if (renderedState.recentRecords.isNotEmpty()) {
                        item {
                            HomeRecentRecordsSection(
                                records = renderedState.recentRecords,
                                settings = renderedState.settings,
                                onOpenHistory = onOpenHistory,
                                onOpenRecord = onOpenRecord,
                            )
                        }
                    }
                }
            },
        )
    }
}
@Composable
private fun HomeOpenAccountCta(
    onManageAccounts: () -> Unit,
) {
    MoneyEmptyStateCard(
        title = stringResource(R.string.home_open_account_required),
        subtitle = stringResource(R.string.home_open_account_required_description),
    ) {
        OutlinedButton(onClick = onManageAccounts) { Text(stringResource(R.string.home_manage_accounts)) }
    }
}

@Composable
fun HomeHeaderActions(
    dueCount: Int,
    onOpenSettings: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.home_settings),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        ReminderHeaderButton(
            dueCount = dueCount,
            onClick = onOpenReminders,
        )
    }
}

@Composable
private fun ReminderHeaderButton(
    dueCount: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = CircleShape,
            ),
    ) {
        BadgedBox(
            badge = {
                if (dueCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = stringResource(R.string.home_reminders),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PeriodOverviewBlock(
    totalAssets: Long,
    cashInflow: Long,
    cashOutflow: Long,
    settings: PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    val cashNet = cashInflow - cashOutflow
    val netColor = when {
        cashNet > 0 -> moneyColors.income
        cashNet < 0 -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(16.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.home_current_net_assets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val recordText = formatInAppAmount(totalAssets, settings)
            val recordStyle = when {
                recordText.length > 12 -> MaterialTheme.typography.headlineSmall
                recordText.length > 8 -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.displayLarge
            }
            Text(
                text = recordText,
                style = recordStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeriodMetricCell(
                    label = stringResource(R.string.home_month_income),
                    value = formatInAppAmount(cashInflow, settings),
                    color = moneyColors.income,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetricCell(
                    label = stringResource(R.string.home_month_expense),
                    value = formatInAppAmount(cashOutflow, settings),
                    color = moneyColors.expense,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetricCell(
                    label = stringResource(R.string.home_month_net_cash_flow),
                    value = formatInAppAmount(cashNet, settings),
                    color = netColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonthlyBudgetBlock(
    budget: MonthlyBudgetStatus?,
    settings: PortableSettings,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    MoneySectionHeader(title = stringResource(R.string.home_monthly_budget))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (budget == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_budget_not_set_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.home_set_monthly_budget),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.home_budget_spent_format,
                            formatInAppAmount(budget.spentAmount, settings),
                            formatInAppAmount(budget.targetAmount, settings),
                        ),
                    )
                    Text(budget.percentageText, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { budget.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (budget.overBudgetAmount != null && budget.overBudgetPercentageText != null) {
                    Text(
                        text = stringResource(
                            R.string.home_budget_over_format,
                            budget.overBudgetPercentageText,
                            formatInAppAmount(budget.overBudgetAmount, settings),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.home_edit_monthly_budget)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.home_close_monthly_budget)) }
                }
            }
        }
    }
}

@Composable
private fun HomeReminderSection(
    reminders: List<DueReminderUiModel>,
    onOpenReminders: () -> Unit,
) {
    MoneySectionHeader(title = stringResource(R.string.home_due_reminders))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (reminders.isEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.home_no_due_reminders), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenReminders) { Text(stringResource(R.string.home_manage_reminders)) }
            }
        } else {
            Column {
                reminders.forEachIndexed { index, reminder ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenReminders)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(reminder.name)
                        Text(reminder.amountFormatted, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index != reminders.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeStaleAccountSection(
    accounts: List<StaleAccountUiModel>,
    settings: PortableSettings,
    onReconcile: (Long) -> Unit,
    onChooseAccount: () -> Unit,
    showReconcileAction: Boolean,
) {
    MoneySectionHeader(title = stringResource(R.string.home_stale_accounts))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (accounts.isEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.home_no_stale_accounts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showReconcileAction) {
                    TextButton(onClick = onChooseAccount) { Text(stringResource(R.string.home_reconcile_account)) }
                }
            }
        } else {
            Column {
                accounts.forEachIndexed { index, account ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReconcile(account.accountId) }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(account.name)
                        Text(
                            formatInAppAmount(account.currentBalance, settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != accounts.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeSavingsGoalBlock(
    progress: SavingsGoalProgress,
    settings: PortableSettings,
    onOpenSavingsGoal: () -> Unit,
) {
    val presentation = netWorthGoalProgressPresentation(
        currentAmount = progress.currentAmount,
        targetAmount = progress.targetAmount,
    )
    MoneySectionHeader(title = stringResource(R.string.home_savings_goal))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSavingsGoal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(
                        R.string.home_savings_goal_progress_format,
                        formatInAppAmount(progress.currentAmount, settings),
                        formatInAppAmount(progress.targetAmount, settings),
                    ),
                )
                Text(presentation.percentageText, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { presentation.geometryPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeRecentRecordsSection(
    records: List<HomeRecentRecordUiModel>,
    settings: PortableSettings,
    onOpenHistory: () -> Unit,
    onOpenRecord: (HomeRecentRecordUiModel) -> Unit,
) {
    MoneySectionHeader(
        title = stringResource(R.string.home_recent_records),
        trailingContent = {
            Text(
                text = stringResource(R.string.home_view_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenHistory),
            )
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            records.forEachIndexed { index, record ->
                HomeRecentRecordRow(
                    record = record,
                    settings = settings,
                    onClick = { onOpenRecord(record) },
                )
                if (index != records.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun HomeRecentRecordRow(
    record: HomeRecentRecordUiModel,
    settings: PortableSettings,
    onClick: () -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val accent = when (record.kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (record.amount > 0) moneyColors.income else moneyColors.expense
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> moneyColors.current
    }
    val kindLabel = homeRecentRecordKindLabel(record)
    val amountColor = when (record.kind) {
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        else -> when {
            record.amount > 0 -> moneyColors.income
            record.amount < 0 -> moneyColors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = accent,
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$kindLabel · ${record.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatInAppAmount(record.amount, settings),
                style = MaterialTheme.typography.titleLarge,
                color = amountColor,
            )
            Text(
                text = recentRecordTimeLabel(record.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun homeRecentRecordKindLabel(record: HomeRecentRecordUiModel): String {
    return when (record.kind) {
        HistoryRecordKind.CASH_FLOW -> stringResource(
            if (record.amount > 0) R.string.history_inflow else R.string.history_outflow,
        )
        HistoryRecordKind.TRANSFER -> stringResource(R.string.history_transfer)
        HistoryRecordKind.BALANCE_UPDATE -> stringResource(
            if (record.amount == 0L) {
                R.string.history_balance_update
            } else {
                R.string.history_reconciliation_adjustment
            },
        )
        HistoryRecordKind.BALANCE_ADJUSTMENT -> stringResource(R.string.history_balance_adjustment)
    }
}

private fun recentRecordTimeLabel(occurredAt: Long): String {
    val now = System.currentTimeMillis()
    return if (DateTimeTextFormatter.formatDateOnly(occurredAt) == DateTimeTextFormatter.formatDateOnly(now)) {
        DateTimeTextFormatter.formatTimeOnly(occurredAt)
    } else {
        DateTimeTextFormatter.formatDateOnly(occurredAt)
    }
}

@Composable
private fun MonthlyBudgetEditorDialog(
    input: String,
    @androidx.annotation.StringRes inputErrorRes: Int?,
    @androidx.annotation.StringRes saveErrorRes: Int?,
    isSaving: Boolean,
    hasBudget: Boolean,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseBudget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (hasBudget) R.string.home_edit_monthly_budget else R.string.home_set_monthly_budget,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.home_monthly_budget_field)) },
                    isError = inputErrorRes != null || saveErrorRes != null,
                    supportingText = {
                        (inputErrorRes ?: saveErrorRes)?.let { Text(stringResource(it)) }
                    },
                    singleLine = true,
                )
                if (hasBudget) {
                    TextButton(onClick = onCloseBudget, enabled = !isSaving) {
                        Text(stringResource(R.string.home_close_monthly_budget))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                Text(stringResource(if (saveErrorRes != null) R.string.action_retry else R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PeriodMetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
    }
}
