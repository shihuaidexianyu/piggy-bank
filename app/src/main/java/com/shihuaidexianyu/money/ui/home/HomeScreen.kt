package com.shihuaidexianyu.money.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.SavingsGoalProgress
import com.shihuaidexianyu.money.domain.usecase.BudgetPace
import com.shihuaidexianyu.money.domain.usecase.MonthlyBudgetStatus
import com.shihuaidexianyu.money.domain.usecase.PeriodDelta
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.RecordKindDot
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    onSelectPeriod: (DashboardPeriod) -> Unit = {},
) {
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val homeLoadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(it, token = "home:$it"))
            onSnackbarMessageShown()
        }
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
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            actions = {
                HomeHeaderActions(
                    dueCount = state.dueReminders.size + state.staleAccountCount,
                    onOpenSettings = onOpenSettings,
                    onOpenReminders = onAllRemindersClick,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
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
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MoneyEmptyStateCard(
                        title = stringResource(R.string.home_create_first_account),
                        subtitle = stringResource(R.string.home_create_first_account_description),
                    ) {
                        MoneyTonalButton(onClick = onCreateAccount) {
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
                            period = renderedState.period,
                            selectedPeriod = renderedState.selectedPeriod,
                            netWorthDelta = renderedState.netWorthDelta,
                            cashInflowDelta = renderedState.cashInflowDelta,
                            cashOutflowDelta = renderedState.cashOutflowDelta,
                            hasInvestmentAccounts = renderedState.hasInvestmentAccounts,
                            investmentAssets = renderedState.investmentAssets,
                            periodInvestmentPnl = renderedState.periodInvestmentPnl,
                            onSelectPeriod = onSelectPeriod,
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
                            pace = renderedState.budgetPace,
                            settings = renderedState.settings,
                            onEdit = onOpenMonthlyBudgetEditor,
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
        icon = Icons.Rounded.AccountBalanceWallet,
    ) {
        MoneyTonalButton(onClick = onManageAccounts) { Text(stringResource(R.string.home_manage_accounts)) }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier.size(48.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    period: DashboardPeriod,
    selectedPeriod: DashboardPeriod,
    netWorthDelta: PeriodDelta,
    cashInflowDelta: PeriodDelta,
    cashOutflowDelta: PeriodDelta,
    hasInvestmentAccounts: Boolean,
    investmentAssets: Long,
    periodInvestmentPnl: Long,
    onSelectPeriod: (DashboardPeriod) -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val cashNet = cashInflow - cashOutflow
    val netColor = when {
        cashNet > 0 -> moneyColors.income
        cashNet < 0 -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_current_net_assets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PeriodSwitcher(selected = selectedPeriod, onSelect = onSelectPeriod)
            }
            val recordText = formatInAppAmount(totalAssets, settings)
            val recordStyle = when {
                recordText.length > 12 -> MaterialTheme.typography.headlineSmall
                recordText.length > 8 -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.displayLarge
            }
            AnimatedContent(
                targetState = recordText,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "netAssetsAmount",
            ) { animatedText ->
                Text(
                    text = animatedText,
                    style = recordStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            DeltaLabel(
                delta = netWorthDelta,
                settings = settings,
                baselineLabel = stringResource(period.sinceStartLabelRes()),
                increaseIsPositive = true,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasInvestmentAccounts) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PeriodMetricCell(
                        label = stringResource(R.string.home_funding_assets),
                        value = formatInAppAmount(totalAssets - investmentAssets, settings),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    PeriodMetricCell(
                        label = stringResource(R.string.home_investment_assets),
                        value = formatInAppAmount(investmentAssets, settings),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
            if (cashInflow > 0L && cashOutflow > 0L) {
                FlowSplitBar(
                    cashInflow = cashInflow,
                    cashOutflow = cashOutflow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val comparisonLabel = stringResource(period.versusPreviousLabelRes())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeriodMetricCell(
                    label = stringResource(period.incomeLabelRes()),
                    value = formatInAppAmount(cashInflow, settings),
                    color = moneyColors.income,
                    delta = cashInflowDelta,
                    // More income than last period is good news; more spending is not.
                    increaseIsPositive = true,
                    baselineLabel = comparisonLabel,
                    settings = settings,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetricCell(
                    label = stringResource(period.expenseLabelRes()),
                    value = formatInAppAmount(cashOutflow, settings),
                    color = moneyColors.expense,
                    delta = cashOutflowDelta,
                    increaseIsPositive = false,
                    baselineLabel = comparisonLabel,
                    settings = settings,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetricCell(
                    label = stringResource(period.netCashFlowLabelRes()),
                    value = formatInAppAmount(cashNet, settings),
                    color = netColor,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasInvestmentAccounts) {
                val pnlColor = when {
                    periodInvestmentPnl > 0L -> moneyColors.income
                    periodInvestmentPnl < 0L -> moneyColors.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(period.investmentPnlLabelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = signedFormatInAppAmount(periodInvestmentPnl, settings),
                        style = MaterialTheme.typography.labelLarge,
                        color = pnlColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


/**
 * Official M3 segmented-button period selector.
 */
@Composable
private fun PeriodSwitcher(
    selected: DashboardPeriod,
    onSelect: (DashboardPeriod) -> Unit,
) {
    val selectorDescription = stringResource(R.string.home_period_selector)
    val haptics = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .width(164.dp)
            .semantics { contentDescription = selectorDescription },
    ) {
        DashboardPeriod.entries.forEachIndexed { index, period ->
            val isSelected = period == selected
            SegmentedButton(
                modifier = Modifier.height(34.dp),
                selected = isSelected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(period)
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DashboardPeriod.entries.size,
                ),
                icon = {},
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                label = {
                    Text(
                        text = stringResource(period.shortLabelRes()),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

/**
 * Renders a signed change as an arrow, an absolute amount, an optional percentage, and a baseline
 * label. The arrow direction always follows the sign of the change, but the colour follows
 * [increaseIsPositive] — spending more than last month is an increase and should still read as a
 * warning, not as growth.
 */
@Composable
private fun DeltaLabel(
    delta: PeriodDelta,
    settings: PortableSettings,
    baselineLabel: String,
    increaseIsPositive: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    val moneyColors = LocalMoneyColors.current
    if (delta.isUnchanged) {
        Text(
            text = stringResource(R.string.home_change_none_format, baselineLabel),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }
    val isFavourable = delta.isIncrease == increaseIsPositive
    val color = if (isFavourable) moneyColors.income else moneyColors.expense
    val amountText = formatInAppAmount(kotlin.math.abs(delta.deltaAmount), settings)
    val text = delta.percentageText?.let { percentage ->
        stringResource(R.string.home_change_with_percent_format, amountText, percentage, baselineLabel)
    } ?: stringResource(R.string.home_change_format, amountText, baselineLabel)
    val directionDescription = stringResource(
        if (delta.isIncrease) R.string.home_change_increase else R.string.home_change_decrease,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (delta.isIncrease) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = directionDescription,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthlyBudgetBlock(
    budget: MonthlyBudgetStatus?,
    pace: BudgetPace?,
    settings: PortableSettings,
    onEdit: () -> Unit,
) {
    MoneySectionHeader(
        title = stringResource(R.string.home_monthly_budget),
        trailingContent = if (budget != null) {
            {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        } else {
            null
        },
    )
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (budget == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            // The whole card opens the editor (mirroring the savings-goal card); closing the
            // budget lives inside the editor dialog, keeping a destructive-leaning action off
            // the always-visible dashboard.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                pace?.let { BudgetPaceRows(pace = it, budget = budget, settings = settings) }
            }
        }
    }
}

/**
 * The burn-down lines under the budget bar: how fast the month is being spent, and where that
 * pace lands. Once the budget is already blown, the projection is redundant with the over-budget
 * line above, so only the pace is shown.
 */
@Composable
private fun BudgetPaceRows(
    pace: BudgetPace,
    budget: MonthlyBudgetStatus,
    settings: PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (pace.daysRemaining > 0) {
                stringResource(
                    R.string.home_budget_pace_format,
                    pace.daysRemaining,
                    formatInAppAmount(pace.dailyAverageSpent, settings),
                )
            } else {
                stringResource(
                    R.string.home_budget_last_day,
                    formatInAppAmount(pace.dailyAverageSpent, settings),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val alreadyOverBudget = budget.overBudgetAmount != null
        when {
            alreadyOverBudget -> Unit
            pace.projectedOverspend != null -> Text(
                text = stringResource(
                    R.string.home_budget_projected_overspend_format,
                    formatInAppAmount(pace.projectedOverspend, settings),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = moneyColors.expense,
            )
            else -> Text(
                text = stringResource(
                    R.string.home_budget_projected_surplus_format,
                    formatInAppAmount(budget.targetAmount - pace.projectedTotalSpend, settings),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = moneyColors.income,
            )
        }
        pace.safeDailySpend?.let { safeDaily ->
            Text(
                text = stringResource(
                    R.string.home_budget_safe_daily_format,
                    formatInAppAmount(safeDaily, settings),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Only rendered when there are due reminders — the caller hides the whole section otherwise. */
@Composable
private fun HomeReminderSection(
    reminders: List<DueReminderUiModel>,
    onOpenReminders: () -> Unit,
) {
    MoneySectionHeader(title = stringResource(R.string.home_due_reminders))
    MoneyListSection {
        reminders.forEachIndexed { index, reminder ->
            MoneyListRow(
                title = reminder.name,
                subtitle = reminder.amountFormatted,
                showChevron = false,
                onClick = onOpenReminders,
            )
            if (index != reminders.lastIndex) {
                MoneySectionDivider()
            }
        }
    }
}

/** Only rendered when there are stale accounts — the caller hides the whole section otherwise. */
@Composable
private fun HomeStaleAccountSection(
    accounts: List<StaleAccountUiModel>,
    settings: PortableSettings,
    onReconcile: (Long) -> Unit,
) {
    MoneySectionHeader(title = stringResource(R.string.home_stale_accounts))
    MoneyListSection {
        accounts.forEachIndexed { index, account ->
            MoneyListRow(
                title = account.name,
                subtitle = formatInAppAmount(account.currentBalance, settings),
                showChevron = false,
                onClick = { onReconcile(account.accountId) },
            )
            if (index != accounts.lastIndex) {
                MoneySectionDivider()
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
    Card(
        onClick = onOpenSavingsGoal,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.home_view_all)) }
        },
    )
    MoneyListSection {
        records.forEachIndexed { index, record ->
            HomeRecentRecordRow(
                record = record,
                settings = settings,
                onClick = { onOpenRecord(record) },
            )
            if (index != records.lastIndex) {
                MoneySectionDivider()
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
    val kindLabel = homeRecentRecordKindLabel(record)
    val amountColor = when (record.kind) {
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        else -> when {
            record.amount > 0 -> moneyColors.income
            record.amount < 0 -> moneyColors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecordKindDot(kind = record.kind, amount = record.amount)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = "$kindLabel · ${record.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatInAppAmount(record.amount, settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                        maxLines = 1,
                    )
                    Text(
                        text = recentRecordTimeLabel(record.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
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
            when {
                record.amount == 0L -> R.string.history_balance_update
                record.isInvestmentAccount && record.amount > 0L -> R.string.history_investment_gain
                record.isInvestmentAccount -> R.string.history_investment_loss
                else -> R.string.history_reconciliation_adjustment
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
    delta: PeriodDelta? = null,
    increaseIsPositive: Boolean = true,
    baselineLabel: String = "",
    settings: PortableSettings = PortableSettings(),
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
        // Only shown when there is a prior period to compare against — a first-ever month would
        // otherwise report a meaningless "up 100%" against a baseline that never existed.
        if (delta != null && delta.baselineAmount > 0L) {
            DeltaLabel(
                delta = delta,
                settings = settings,
                baselineLabel = baselineLabel,
                increaseIsPositive = increaseIsPositive,
            )
        }
    }
}

@androidx.annotation.StringRes
private fun DashboardPeriod.shortLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_period_week
    DashboardPeriod.MONTH -> R.string.home_period_month
    DashboardPeriod.YEAR -> R.string.home_period_year
}

@androidx.annotation.StringRes
private fun DashboardPeriod.incomeLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_week_income
    DashboardPeriod.MONTH -> R.string.home_month_income
    DashboardPeriod.YEAR -> R.string.home_year_income
}

@androidx.annotation.StringRes
private fun DashboardPeriod.expenseLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_week_expense
    DashboardPeriod.MONTH -> R.string.home_month_expense
    DashboardPeriod.YEAR -> R.string.home_year_expense
}

@androidx.annotation.StringRes
private fun DashboardPeriod.netCashFlowLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_week_net_cash_flow
    DashboardPeriod.MONTH -> R.string.home_month_net_cash_flow
    DashboardPeriod.YEAR -> R.string.home_year_net_cash_flow
}

@androidx.annotation.StringRes
private fun DashboardPeriod.investmentPnlLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_week_investment_pnl
    DashboardPeriod.MONTH -> R.string.home_month_investment_pnl
    DashboardPeriod.YEAR -> R.string.home_year_investment_pnl
}

@androidx.annotation.StringRes
private fun DashboardPeriod.sinceStartLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_since_week_start
    DashboardPeriod.MONTH -> R.string.home_since_month_start
    DashboardPeriod.YEAR -> R.string.home_since_year_start
}

@androidx.annotation.StringRes
private fun DashboardPeriod.versusPreviousLabelRes(): Int = when (this) {
    DashboardPeriod.WEEK -> R.string.home_vs_previous_week
    DashboardPeriod.MONTH -> R.string.home_vs_previous_month
    DashboardPeriod.YEAR -> R.string.home_vs_previous_year
}

@Composable
private fun FlowSplitBar(
    cashInflow: Long,
    cashOutflow: Long,
    modifier: Modifier = Modifier,
) {
    val moneyColors = LocalMoneyColors.current
    val total = (cashInflow + cashOutflow).coerceAtLeast(1L)
    val inflowFraction = cashInflow.toFloat() / total.toFloat()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
    ) {
        val gap = 2.dp.toPx()
        val inflowWidth = (size.width - gap) * inflowFraction
        drawRoundRect(
            color = moneyColors.income,
            size = androidx.compose.ui.geometry.Size(inflowWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            color = moneyColors.expense,
            topLeft = androidx.compose.ui.geometry.Offset(inflowWidth + gap, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - inflowWidth - gap, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}
