package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.common.accountVisualColor
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import java.math.BigInteger

internal fun shouldShowAccountOverview(state: AccountsUiState): Boolean =
    state.openAccounts.isNotEmpty() || state.closedAccounts.isNotEmpty()

data class AccountGroups(
    val normal: List<AccountListItemUiModel>,
    val hidden: List<AccountListItemUiModel>,
    val closed: List<AccountListItemUiModel>,
) {
    val all: List<AccountListItemUiModel> get() = normal + hidden + closed
}

fun accountGroups(
    openAccounts: List<AccountListItemUiModel>,
    closedAccounts: List<AccountListItemUiModel>,
): AccountGroups = AccountGroups(
    normal = openAccounts.filterNot(AccountListItemUiModel::isHidden),
    hidden = openAccounts.filter(AccountListItemUiModel::isHidden),
    closed = closedAccounts,
)

data class NetWorthGoalProgressPresentation(
    val geometryPercent: Int,
    val percentageText: String,
)

fun netWorthGoalProgressPresentation(
    currentAmount: Long,
    targetAmount: Long,
): NetWorthGoalProgressPresentation {
    require(targetAmount > 0L) { "净资产目标必须大于 0" }
    val percentage = BigInteger.valueOf(currentAmount)
        .multiply(BigInteger.valueOf(100L))
        .divide(BigInteger.valueOf(targetAmount))
    val geometryPercent = when {
        percentage.signum() <= 0 -> 0
        percentage >= BigInteger.valueOf(100L) -> 100
        else -> percentage.toInt()
    }
    return NetWorthGoalProgressPresentation(
        geometryPercent = geometryPercent,
        percentageText = "$percentage%",
    )
}

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleClosedVisibility: () -> Unit,
    onManageSavingsGoal: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val groups = accountGroups(state.openAccounts, state.closedAccounts)
    val hasClosedAccounts = state.closedAccounts.isNotEmpty()
    val positiveAssetsTotal = (state.openAccounts + state.closedAccounts)
        .mapNotNull { account -> account.balance.takeIf { it > 0L } }
        .ledgerSumExact()

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = stringResource(R.string.accounts_title),
            trailing = {
                IconButton(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.accounts_create),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val asyncContent = state.toAsyncContent()
            if (asyncContent is AsyncContent.Loading || asyncContent is AsyncContent.Error) {
                item {
                    AsyncContentRenderer(
                        content = asyncContent,
                        onRetry = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        data = { _, _ -> },
                    )
                }
                return@LazyColumn
            }
            if (shouldShowAccountOverview(state)) {
                item {
                    AccountOverviewCard(state = state)
                }
            }
            item {
                MoneySectionHeader(title = stringResource(R.string.accounts_goal_section))
            }
            item {
                NetWorthGoalCard(
                    goal = state.savingsGoal,
                    settings = state.settings,
                    onClick = onManageSavingsGoal,
                )
            }
            if (state.openAccounts.isEmpty()) {
                item {
                    MoneyEmptyStateCard(
                            title = stringResource(
                                if (hasClosedAccounts) R.string.accounts_no_open else R.string.accounts_none,
                            ),
                            subtitle = stringResource(
                                if (hasClosedAccounts) R.string.accounts_closed_hint else R.string.accounts_empty_hint,
                            ),
                    ) {
                        if (hasClosedAccounts) {
                            OutlinedButton(onClick = onToggleClosedVisibility) {
                                Text(
                                    stringResource(
                                        if (state.showClosed) {
                                            R.string.accounts_collapse_closed
                                        } else {
                                            R.string.accounts_show_closed
                                        },
                                    ),
                                )
                            }
                        } else {
                            OutlinedButton(onClick = onCreateAccount) {
                                Text(stringResource(R.string.accounts_create_first))
                            }
                        }
                    }
                }
            } else {
                if (groups.normal.isNotEmpty()) {
                    val staleCount = groups.normal.count { it.isStale }
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.accounts_normal),
                            trailing = if (staleCount > 0) {
                                pluralStringResource(R.plurals.stale_account_count, staleCount, staleCount)
                            } else {
                                pluralStringResource(R.plurals.account_count, groups.normal.size, groups.normal.size)
                            },
                        )
                    }
                    itemsIndexed(groups.normal, key = { _, account -> account.id }) { _, account ->
                        AccountCard(
                            account = account,
                            currencySettings = state.settings,
                            positiveAssetsTotal = positiveAssetsTotal,
                            onClick = { onAccountClick(account.id) },
                        )
                    }
                }
                if (groups.hidden.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.accounts_hidden),
                            trailing = pluralStringResource(
                                R.plurals.account_count,
                                groups.hidden.size,
                                groups.hidden.size,
                            ),
                        )
                    }
                    itemsIndexed(groups.hidden, key = { _, account -> account.id }) { _, account ->
                        AccountCard(
                            account = account,
                            currencySettings = state.settings,
                            positiveAssetsTotal = positiveAssetsTotal,
                            onClick = { onAccountClick(account.id) },
                        )
                    }
                }
            }
            if (hasClosedAccounts) {
                item {
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        MoneyListRow(
                            title = stringResource(R.string.accounts_closed),
                            trailing = pluralStringResource(
                                R.plurals.account_count,
                                state.closedAccounts.size,
                                state.closedAccounts.size,
                            ),
                            showChevron = false,
                            accessory = {
                                Text(
                                    text = stringResource(
                                        if (state.showClosed) R.string.action_collapse else R.string.action_view,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            },
                            modifier = Modifier.clickable(onClick = onToggleClosedVisibility),
                        )
                    }
                }
            }
            if (state.showClosed) {
                item {
                    MoneySectionHeader(title = stringResource(R.string.accounts_closed))
                }
                itemsIndexed(state.closedAccounts, key = { _, account -> account.id }) { _, account ->
                    AccountCard(
                        account = account,
                        currencySettings = state.settings,
                        positiveAssetsTotal = 0L,
                        onClick = { onAccountClick(account.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountOverviewCard(state: AccountsUiState) {
    val totalAssets = (state.openAccounts + state.closedAccounts)
        .map(AccountListItemUiModel::balance)
        .ledgerSumExact()
    val staleCount = state.openAccounts.count { it.isStale }
    val current = LocalMoneyColors.current.current
    MoneyCard(contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.accounts_total_assets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatInAppAmount(totalAssets, state.settings),
                    style = when {
                        totalAssets.toString().length > 10 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.headlineSmall
                    },
                    color = current,
                )
            }
            if (staleCount > 0) {
                MoneyStatusPill(
                    text = pluralStringResource(R.plurals.stale_account_count, staleCount, staleCount),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (staleCount > 0) {
            Text(
                text = stringResource(R.string.accounts_stale_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("FloatingPointUsageInMoney")
@Composable
private fun NetWorthGoalCard(
    goal: SavingsGoalUiModel?,
    settings: PortableSettings,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = MaterialTheme.colorScheme.primary
    val progress = goal?.let {
        netWorthGoalProgressPresentation(it.currentAmount, it.targetAmount)
    }
    val fillFraction = (progress?.geometryPercent ?: 0) / 100f
    val currentText = goal?.let { formatInAppAmount(it.currentAmount, settings) }
    val targetText = goal?.let { formatInAppAmount(it.targetAmount, settings) }
    val isAchieved = goal?.isAchieved == true
    val statusText = when {
        goal == null -> stringResource(R.string.accounts_goal_empty_hint)
        isAchieved -> stringResource(R.string.accounts_goal_achieved)
        else -> stringResource(R.string.accounts_goal_in_progress)
    }
    val semantics = if (goal == null) {
        stringResource(R.string.accounts_goal_set)
    } else {
        stringResource(
            R.string.accounts_goal_card_semantics,
            requireNotNull(currentText),
            requireNotNull(targetText),
            requireNotNull(progress).percentageText,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f),
                shape = shape,
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = semantics
                role = Role.Button
            },
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fillFraction > 0f) {
                        Modifier.drawBehind {
                            drawRect(
                                color = accent.copy(alpha = 0.12f),
                                size = Size(
                                    width = size.width * fillFraction.coerceIn(0f, 1f),
                                    height = size.height,
                                ),
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp).size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accounts_net_worth_goal),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = if (targetText == null) {
                            statusText
                        } else {
                            stringResource(R.string.accounts_goal_target_format, targetText)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (progress == null || currentText == null) {
                        Text(
                            text = stringResource(R.string.accounts_goal_set_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                        )
                    } else {
                        Text(
                            text = progress.percentageText,
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                        )
                        Text(
                            text = currentText,
                            style = if (currentText.length > 14) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAchieved) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountListItemUiModel,
    currencySettings: PortableSettings,
    positiveAssetsTotal: Long,
    onClick: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
    val balanceText = formatInAppAmount(account.balance, currencySettings)
    val showAssetShare = !account.isClosed && account.balance > 0 && positiveAssetsTotal > 0
    // Fraction is for UI layout only (a horizontal rectangle width). Compose draw APIs take Float,
    // so we divide as Float *after* the rule-of-money check. The amount itself is never stored as Float.
    @Suppress("FloatingPointUsageInMoney")
    val assetShareFraction = if (showAssetShare) {
        account.balance.toFloat() / positiveAssetsTotal.toFloat()
    } else {
        0f
    }
    val assetShareText = if (showAssetShare) {
        formatAssetShare(account.balance, positiveAssetsTotal)
    } else {
        ""
    }
    val assetShareColor = accountVisualColor(account.colorName)
    val statusText = when {
        account.requiresReopenAndSettle -> stringResource(R.string.account_status_reopen_settle)
        account.isClosed -> stringResource(R.string.account_status_closed)
        account.isHidden -> stringResource(R.string.account_status_hidden)
        account.isStale -> stringResource(R.string.account_status_stale)
        else -> stringResource(R.string.account_status_normal)
    }
    val balanceStyle = when {
        balanceText.length > 18 -> MaterialTheme.typography.bodyMedium
        balanceText.length > 14 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val balanceSemantics = stringResource(R.string.account_balance_semantics_format, balanceText)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(account.name)
                    append(balanceSemantics)
                    append("，$statusText")
                }
                role = Role.Button
            },
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showAssetShare) {
                        Modifier.drawBehind {
                            drawRect(
                                color = assetShareColor.copy(alpha = 0.10f),
                                size = Size(
                                    width = size.width * assetShareFraction.coerceIn(0.01f, 1f),
                                    height = size.height,
                                ),
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountIconBadge(
                    iconName = account.iconName,
                    colorName = account.colorName,
                    isClosed = account.isClosed,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showAssetShare) {
                        Text(
                            text = assetShareText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = balanceText,
                        style = balanceStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (account.isStale && !account.isClosed) {
                        MoneyStatusPill(
                            text = stringResource(R.string.account_stale_badge),
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatAssetShare(balance: Long, totalPositiveBalance: Long): String {
    // Integer percentage of [0, 100] — uses Long arithmetic to avoid Float/Double for the money math.
    val percentage = (balance * 100L / totalPositiveBalance).coerceIn(0L, 100L)
    return if (percentage < 1L) {
        "<1%"
    } else {
        "$percentage%"
    }
}

