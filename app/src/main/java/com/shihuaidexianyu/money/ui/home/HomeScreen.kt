package com.shihuaidexianyu.money.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.shihuaidexianyu.money.ui.common.RecordKindBadge
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.formatSharePercent
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
    onOpenHistory: () -> Unit = {},
    onOpenRecord: (HomeRecentRecordUiModel) -> Unit = {},
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
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NetWorthHeroCard(
            totalAssets = totalAssets,
            settings = settings,
            period = period,
            selectedPeriod = selectedPeriod,
            hasInvestmentAccounts = hasInvestmentAccounts,
            investmentAssets = investmentAssets,
            onSelectPeriod = onSelectPeriod,
        )
        PeriodFlowsCard(
            cashInflow = cashInflow,
            cashOutflow = cashOutflow,
            settings = settings,
            period = period,
            hasInvestmentAccounts = hasInvestmentAccounts,
            periodInvestmentPnl = periodInvestmentPnl,
        )
    }
}

/**
 * First story on home: how much money there is right now. The period switcher lives here;
 * everything about how money moved lives in [PeriodFlowsCard].
 */
@Composable
private fun NetWorthHeroCard(
    totalAssets: Long,
    settings: PortableSettings,
    period: DashboardPeriod,
    selectedPeriod: DashboardPeriod,
    hasInvestmentAccounts: Boolean,
    investmentAssets: Long,
    onSelectPeriod: (DashboardPeriod) -> Unit,
) {
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
            RollingAmountText(
                target = recordText,
                style = recordStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (hasInvestmentAccounts) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
                val fundingAssets = totalAssets - investmentAssets
                val fundingShare = formatSharePercent(fundingAssets, totalAssets)
                val investmentShare = if (totalAssets > 0L && investmentAssets > 0L) {
                    val fundingPercent = fundingAssets * 100L / totalAssets
                    val investmentPercent = (100L - fundingPercent).coerceIn(0L, 100L)
                    if (investmentPercent < 1L) "<1%" else "$investmentPercent%"
                } else {
                    ""
                }
                // Teal vs amber: the palette's warm accent keeps the two asset kinds clearly
                // distinguishable where teal/slate were too close.
                val fundingColor = MaterialTheme.colorScheme.primary
                val investmentColor = LocalMoneyColors.current.current
                // Legacy ledgers can go negative; a proportion bar only makes sense for a
                // positive, fully-attributed total.
                if (fundingAssets >= 0L && investmentAssets >= 0L && totalAssets > 0L) {
                    AssetSplitBar(
                        fundingAssets = fundingAssets,
                        investmentAssets = investmentAssets,
                        fundingColor = fundingColor,
                        investmentColor = investmentColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AssetSplitCell(
                        label = stringResource(R.string.home_funding_assets),
                        value = formatInAppAmount(fundingAssets, settings),
                        share = fundingShare,
                        dotColor = fundingColor,
                        modifier = Modifier.weight(1f),
                    )
                    AssetSplitCell(
                        label = stringResource(R.string.home_investment_assets),
                        value = formatInAppAmount(investmentAssets, settings),
                        share = investmentShare,
                        dotColor = investmentColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Odometers-style amount display: when the amount changes, digits that differ roll vertically
 * (new value enters from below on increase, from above on decrease) while unchanged characters,
 * the currency symbol and separators stay put. Reuses the app typography so tabular figures and
 * the font keep the exact look of a single Text.
 */
@Composable
private fun RollingAmountText(
    target: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(target) }
    var previous by remember { mutableStateOf(target) }
    if (target != current) {
        previous = current
        current = target
    }
    val chars = current.toList()
    val prevChars = previous.toList()
    val offset = chars.size - prevChars.size
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        chars.forEachIndexed { index, char ->
            val prevChar = prevChars.getOrNull(index - offset)
            if (prevChar == null || prevChar == char) {
                Text(text = char.toString(), style = style, color = color)
            } else {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val rising = charValue(targetState) >= charValue(initialState)
                        val direction = if (rising) 1 else -1
                        (slideInVertically(animationSpec = tween(220)) { direction * it } +
                            fadeIn(animationSpec = tween(220))) togetherWith
                            (slideOutVertically(animationSpec = tween(220)) { -direction * it } +
                                fadeOut(animationSpec = tween(160)))
                    },
                    label = "rollingAmountChar",
                ) { animatedChar ->
                    Text(text = animatedChar.toString(), style = style, color = color)
                }
            }
        }
    }
}

private fun charValue(char: Char): Int = when (char) {
    in '0'..'9' -> char - '0'
    else -> -1
}

/**
 * Secondary asset split line in the hero card. Kept deliberately quiet: neutral text, no semantic
 * colors, so the card never competes with the headline amount.
 */
@Composable
private fun AssetSplitCell(
    label: String,
    value: String,
    share: String? = null,
    dotColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                // Matches the segment color in the AssetSplitBar above, so each cell reads as the
                // legend for its bar segment.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        share?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Second story on home: how cash moved during the selected period. The net figure is the
 * headline — "did this period save money" is the question the card answers — with income and
 * expense as the supporting cells, mirroring the hero card's headline-plus-breakdown language.
 */
@Composable
private fun PeriodFlowsCard(
    cashInflow: Long,
    cashOutflow: Long,
    settings: PortableSettings,
    period: DashboardPeriod,
    hasInvestmentAccounts: Boolean,
    periodInvestmentPnl: Long,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(period.netCashFlowLabelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = formatInAppAmount(cashNet, settings),
                    style = MaterialTheme.typography.headlineSmall,
                    color = netColor,
                    maxLines = 1,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeriodMetricCell(
                    label = stringResource(period.incomeLabelRes()),
                    value = formatInAppAmount(cashInflow, settings),
                    color = moneyColors.income,
                    modifier = Modifier.weight(1f),
                )
                PeriodMetricCell(
                    label = stringResource(period.expenseLabelRes()),
                    value = formatInAppAmount(cashOutflow, settings),
                    color = moneyColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            // Zero P&L is the common case for investment accounts in quiet periods; the row only
            // earns its place when there is something to report.
            if (hasInvestmentAccounts && periodInvestmentPnl != 0L) {
                val pnlColor = if (periodInvestmentPnl > 0L) moneyColors.income else moneyColors.expense
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
 * Compact pill selector restored to the previous visual language: a quiet tonal container with
 * an elevated selected segment. The height is raised from the original 30dp to a 40dp touch
 * target while keeping the Material interaction and selection semantics.
 */
@Composable
private fun PeriodSwitcher(
    selected: DashboardPeriod,
    onSelect: (DashboardPeriod) -> Unit,
) {
    val selectorDescription = stringResource(R.string.home_period_selector)
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .width(146.dp)
            .semantics { contentDescription = selectorDescription },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DashboardPeriod.entries.forEach { period ->
                val isSelected = period == selected
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(period)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .semantics { this.selected = isSelected },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(period.shortLabelRes()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
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
    // Same row language as the history ledger (V1): type badge, "account · time" subtitle, and a
    // signed amount. Transfers stay neutral (unsigned, transfer color). The running balance is
    // deliberately omitted here — rows from different accounts interleave, so a per-row account
    // balance reads as noise on the home dashboard.
    val amountText = when (record.kind) {
        HistoryRecordKind.TRANSFER -> formatInAppAmount(record.amount, settings)
        else -> signedFormatInAppAmount(record.amount, settings)
    }
    val amountColor = when (record.kind) {
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        else -> when {
            record.amount > 0 -> moneyColors.income
            record.amount < 0 -> moneyColors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val timeLabel = DateTimeTextFormatter.formatCompactDayTime(record.occurredAt, System.currentTimeMillis())
    val subtitleText = if (record.subtitle.isBlank()) timeLabel else "${record.subtitle} · $timeLabel"
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecordKindBadge(kind = record.kind, amount = record.amount)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                maxLines = 1,
            )
        }
    }
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

/** Two-segment proportion bar for the funding/investment asset split; colors come from the
 * theme palette (not income/expense colors, which carry flow semantics). */
@Composable
private fun AssetSplitBar(
    fundingAssets: Long,
    investmentAssets: Long,
    fundingColor: Color,
    investmentColor: Color,
    modifier: Modifier = Modifier,
) {
    val total = (fundingAssets + investmentAssets).coerceAtLeast(1L)
    val fundingFraction = (fundingAssets.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
    ) {
        val gap = 2.dp.toPx()
        val fundingWidth = (size.width - gap) * fundingFraction
        drawRoundRect(
            color = fundingColor,
            size = androidx.compose.ui.geometry.Size(fundingWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            color = investmentColor,
            topLeft = androidx.compose.ui.geometry.Offset(fundingWidth + gap, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - fundingWidth - gap, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}
