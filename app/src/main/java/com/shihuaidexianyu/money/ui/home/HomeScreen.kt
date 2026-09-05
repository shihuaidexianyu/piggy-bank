package com.shihuaidexianyu.money.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.PortableSettings
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
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
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
    onSelectPeriod: (DashboardPeriod) -> Unit = {},
) {
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val homeLoadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
    val manageAccountsLabel = stringResource(R.string.accounts_management)

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            rootSnackbarDispatcher?.dispatch(
                rootSnackbarEffect(
                    message = it,
                    actionLabel = manageAccountsLabel,
                    action = RootSnackbarAction.ManageAccounts,
                    token = "home:$it",
                ),
            )
            onSnackbarMessageShown()
        }
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            actions = {
                HomeHeaderActions(
                    // The badge counts due reminders only; stale accounts live in their own
                    // section below and are not "things to act on from the bell".
                    dueCount = state.dueReminders.size,
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
                    verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingXl),
                ) {
                    item {
                        PeriodOverviewBlock(
                            totalAssets = renderedState.totalAssets,
                            cashInflow = renderedState.periodCashInflow,
                            cashOutflow = renderedState.periodCashOutflow,
                            settings = renderedState.settings,
                            period = renderedState.period,
                            selectedPeriod = renderedState.selectedPeriod,
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
    Row(
        modifier = Modifier.padding(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularHeaderIconButton(
            onClick = onOpenSettings,
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

/**
 * Header icon with a quiet circular tonal backing, so settings/reminder actions read as buttons
 * instead of floating glyphs on the app-bar canvas.
 */
@Composable
private fun CircularHeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ReminderHeaderButton(
    dueCount: Int,
    onClick: () -> Unit,
) {
    CircularHeaderIconButton(
        onClick = onClick,
    ) {
        BadgedBox(
            badge = {
                if (dueCount > 0) {
                    Badge { Text(text = dueCount.toString()) }
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
    hasInvestmentAccounts: Boolean,
    investmentAssets: Long,
    periodInvestmentPnl: Long,
    onSelectPeriod: (DashboardPeriod) -> Unit,
) {
    val colors = LocalMoneyColors.current
    Column(verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingXxl)) {
        Column(verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingMd)) {
            Text(
                text = stringResource(R.string.home_current_net_assets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val amount = formatInAppAmount(totalAssets, settings)
            Text(
                text = amount,
                style = if (amount.length > 12) MaterialTheme.typography.displayMedium
                    else MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasInvestmentAccounts) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingXl),
                ) {
                    AssetAmount(
                        label = stringResource(R.string.home_funding_assets),
                        amount = formatInAppAmount(totalAssets - investmentAssets, settings),
                        modifier = Modifier.weight(1f),
                    )
                    AssetAmount(
                        label = stringResource(R.string.home_investment_assets),
                        amount = formatInAppAmount(investmentAssets, settings),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingLg)) {
            MoneySectionHeader(title = stringResource(R.string.home_period_activity))
            PeriodSwitcher(selected = selectedPeriod, onSelect = onSelectPeriod)
            PeriodAmountRow(
                label = stringResource(period.incomeLabelRes()),
                value = formatInAppAmount(cashInflow, settings),
                color = colors.income,
            )
            PeriodAmountRow(
                label = stringResource(period.expenseLabelRes()),
                value = formatInAppAmount(cashOutflow, settings),
                color = colors.expense,
            )
            PeriodAmountRow(
                label = stringResource(period.netCashFlowLabelRes()),
                value = signedFormatInAppAmount(cashInflow - cashOutflow, settings),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasInvestmentAccounts) {
                PeriodAmountRow(
                    label = stringResource(period.investmentPnlLabelRes()),
                    value = signedFormatInAppAmount(periodInvestmentPnl, settings),
                    color = when {
                        periodInvestmentPnl > 0L -> colors.income
                        periodInvestmentPnl < 0L -> colors.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AssetAmount(label: String, amount: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingXs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PeriodAmountRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
            color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PeriodSwitcher(selected: DashboardPeriod, onSelect: (DashboardPeriod) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val description = stringResource(R.string.home_period_selector)
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingSm),
    ) {
        DashboardPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(period)
                },
                modifier = Modifier.weight(1f).semantics { this.selected = isSelected },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(period.shortLabelRes()), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Only rendered when there are due reminders — the caller hides the whole section otherwise. */
@Composable
private fun HomeReminderSection(
    reminders: List<DueReminderUiModel>,
    onOpenReminders: () -> Unit,
) {
    val todayLabel = stringResource(R.string.history_today)
    val tomorrowLabel = stringResource(R.string.home_tomorrow)
    val nowMillis = System.currentTimeMillis()
    MoneySectionHeader(title = stringResource(R.string.home_due_reminders))
    MoneyListSection {
        reminders.forEachIndexed { index, reminder ->
            MoneyListRow(
                title = reminder.name,
                subtitle = stringResource(
                    R.string.home_due_reminder_subtitle_format,
                    DateTimeTextFormatter.formatRelativeDayTime(
                        timeMillis = reminder.dueAt,
                        nowMillis = nowMillis,
                        todayLabel = todayLabel,
                        tomorrowLabel = tomorrowLabel,
                    ),
                    reminder.accountName,
                ),
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
                subtitle = formatInAppAmount(account.currentBalance, settings) +
                    " · " + staleAccountCheckedText(account.lastBalanceUpdateAt),
                onClick = { onReconcile(account.accountId) },
            )
            if (index != accounts.lastIndex) {
                MoneySectionDivider()
            }
        }
    }
}

@Composable
private fun staleAccountCheckedText(lastBalanceUpdateAt: Long?): String {
    val millis = lastBalanceUpdateAt ?: return stringResource(R.string.balance_never_checked)
    val zone = java.time.ZoneId.systemDefault()
    val lastDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS
        .between(lastDate, java.time.LocalDate.now(zone))
        .coerceAtLeast(0L)
        .toInt()
    return if (days == 0) {
        stringResource(R.string.balance_checked_today)
    } else {
        stringResource(R.string.balance_last_checked_days_format, days)
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
