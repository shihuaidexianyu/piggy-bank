package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.formatSharePercent
import com.shihuaidexianyu.money.ui.common.formatInAppAmount

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleClosedVisibility: () -> Unit,
    onManageSavingsGoal: () -> Unit,
    onReorderAccounts: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val groups = accountGroups(state.openAccounts, state.closedAccounts)
    val hasClosedAccounts = state.closedAccounts.isNotEmpty()
    val positiveAssetsTotal = (state.openAccounts + state.closedAccounts)
        .mapNotNull { account -> account.balance.takeIf { it > 0L } }
        .sum()
    val normalKindGroups = buildList<Pair<AccountKind?, List<AccountListItemUiModel>>> {
        val funding = groups.normal.filter { it.kind == AccountKind.FUNDING }
        val investment = groups.normal.filter { it.kind == AccountKind.INVESTMENT }
        if (funding.isNotEmpty() && investment.isNotEmpty()) {
            add(AccountKind.FUNDING to funding)
            add(AccountKind.INVESTMENT to investment)
        } else {
            add(null to groups.normal)
        }
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(stringResource(R.string.accounts_title)) },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = MoneyDimens.bottomNavContentPadding),
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
            if (state.openAccounts.isNotEmpty() || hasClosedAccounts) {
                item {
                    AccountsOverviewCard(
                        openCount = state.openAccounts.size,
                        staleCount = state.openAccounts.count { it.isStale },
                    )
                }
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
                            icon = Icons.Rounded.AccountBalanceWallet,
                    ) {
                        if (hasClosedAccounts) {
                            MoneyTonalButton(onClick = onToggleClosedVisibility) {
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
                            MoneyTonalButton(onClick = onCreateAccount) {
                                Text(stringResource(R.string.accounts_create_first))
                            }
                        }
                    }
                }
            } else {
                normalKindGroups.forEachIndexed { groupIndex, (kind, accounts) ->
                    if (accounts.isNotEmpty()) {
                        val staleCount = accounts.count { it.isStale }
                        item {
                            MoneySectionHeader(
                                title = stringResource(
                                    when (kind) {
                                        AccountKind.FUNDING -> R.string.account_kind_funding
                                        AccountKind.INVESTMENT -> R.string.account_kind_investment
                                        null -> R.string.accounts_normal
                                    },
                                ),
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (groupIndex == 0) {
                                            IconButton(
                                                onClick = onReorderAccounts,
                                                modifier = Modifier.size(40.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Reorder,
                                                    contentDescription = stringResource(R.string.accounts_order),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (staleCount > 0) {
                                                pluralStringResource(
                                                    R.plurals.stale_account_count,
                                                    staleCount,
                                                    staleCount,
                                                )
                                            } else {
                                                pluralStringResource(
                                                    R.plurals.account_count,
                                                    accounts.size,
                                                    accounts.size,
                                                )
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                        itemsIndexed(accounts, key = { _, account -> account.id }) { _, account ->
                            AccountCard(
                                account = account,
                                currencySettings = state.settings,
                                positiveAssetsTotal = positiveAssetsTotal,
                                onClick = { onAccountClick(account.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
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
                            modifier = Modifier.animateItem(),
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
                            onClick = onToggleClosedVisibility,
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
                        modifier = Modifier.animateItem(),
                        onClick = { onAccountClick(account.id) },
                    )
                }
            }
            item {
                MoneySectionHeader(title = stringResource(R.string.account_management_title))
            }
            item {
                MoneyCard(contentPadding = PaddingValues(0.dp)) {
                    MoneyListRow(
                        title = stringResource(
                            if (state.savingsGoal == null) {
                                R.string.savings_goal_set_title
                            } else {
                                R.string.savings_goal_edit_title
                            },
                        ),
                        subtitle = stringResource(R.string.accounts_goal_manage_description),
                        onClick = onManageSavingsGoal,
                    )
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
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val balanceText = formatInAppAmount(account.balance, currencySettings)
    val statusText = when {
        account.requiresReopenAndSettle -> stringResource(R.string.account_status_reopen_settle)
        account.isClosed -> stringResource(R.string.account_status_closed)
        account.isHidden -> stringResource(R.string.account_status_hidden)
        account.isStale -> stringResource(R.string.account_status_stale)
        else -> null
    }
    // Normal accounts stay silent; only investment accounts carry a type tag so the kind is
    // visible while scrolling without repeating it on every row.
    val caption = statusText ?: if (account.kind == AccountKind.INVESTMENT) {
        stringResource(R.string.account_kind_investment)
    } else {
        null
    }
    val isDimmed = account.isClosed || account.isHidden
    val balanceStyle = when {
        balanceText.length > 18 -> MaterialTheme.typography.bodyMedium
        balanceText.length > 14 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val balanceSemantics = stringResource(R.string.account_balance_semantics_format, balanceText)
    val shareText = if (!account.isClosed && account.balance > 0L && positiveAssetsTotal > 0L) {
        formatSharePercent(account.balance, positiveAssetsTotal)
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(account.name)
                    append(balanceSemantics)
                    caption?.let { append("，$it") }
                }
                role = Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = MaterialTheme.shapes.medium,
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
                    color = if (isDimmed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = balanceText,
                    style = balanceStyle,
                    color = if (account.isClosed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    maxLines = 1,
                )
                shareText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Page-level summary limited to what the home dashboard does not already show: open and stale
 * account counts. The total-assets amount and the funding/investment split live on home only,
 * so each piece of information keeps a single source.
 */
@Composable
private fun AccountsOverviewCard(
    openCount: Int,
    staleCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.accounts_open_count_format, openCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
            )
            if (staleCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.stale_account_count, staleCount, staleCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
