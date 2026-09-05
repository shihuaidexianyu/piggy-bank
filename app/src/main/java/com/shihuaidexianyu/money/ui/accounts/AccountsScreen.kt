package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.formatSharePercent

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
    onReorderAccounts: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onBatchReconcile: () -> Unit = {},
) {
    val groups = accountGroups(state.openAccounts, state.closedAccounts)
    val hasClosedAccounts = state.closedAccounts.isNotEmpty()
    // Share denominator for every open row: the full open-book total (hidden accounts included —
    // hiding never changes calculations). Closed accounts sit at zero and simply show no share.
    val openTotalBalance = (groups.normal + groups.hidden).map { it.balance }.ledgerSumExact()
    val loadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
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
            actions = {
                // Same tonal-circle language as the home header actions.
                Row(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.openAccounts.isNotEmpty()) {
                        IconButton(
                            onClick = onReorderAccounts,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Reorder,
                                contentDescription = stringResource(R.string.accounts_order),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onCreateAccount,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.accounts_create),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = MoneyDimens.screenHorizontalPadding, top = 8.dp, end = MoneyDimens.screenHorizontalPadding, bottom = MoneyDimens.bottomNavContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val asyncContent = state.toAsyncContent(loadErrorMessage)
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
                        MoneyTonalButton(onClick = onCreateAccount) {
                            Text(stringResource(R.string.accounts_create_first))
                        }
                    }
                }
            } else {
                val staleCount = state.openAccounts.count { it.isStale }
                if (staleCount > 0) {
                    item {
                        MoneyListRow(
                            title = stringResource(R.string.accounts_need_check_format, staleCount),
                            trailing = stringResource(R.string.balance_reconcile_title),
                            onClick = onBatchReconcile,
                        )
                    }
                }
                normalKindGroups.forEach { (kind, accounts) ->
                    if (accounts.isNotEmpty()) {
                        val staleCount = accounts.count { it.isStale }
                        item {
                            MoneySectionHeader(
                                title = stringResource(
                                    when (kind) {
                                        AccountKind.FUNDING -> R.string.account_kind_funding
                                        AccountKind.INVESTMENT -> R.string.account_kind_investment
                                        null -> if (accounts.firstOrNull()?.kind == AccountKind.INVESTMENT) {
                                            R.string.account_kind_investment
                                        } else {
                                            R.string.account_kind_funding
                                        }
                                    },
                                ),
                                trailing = if (staleCount > 0) {
                                    stringResource(
                                        R.string.accounts_group_total_stale_format,
                                        accounts.size,
                                        staleCount,
                                    )
                                } else {
                                    stringResource(
                                        R.string.accounts_group_total_format,
                                        accounts.size,
                                    )
                                },
                            )
                        }
                        item {
                            // One grouped card per section (the history list's language) instead of
                            // a card per account — the per-account pastel cards read heavy and
                            // wasted a full card's padding on at most two lines of text.
                            MoneyCard(
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.animateItem(),
                            ) {
                                accounts.forEachIndexed { index, account ->
                                    AccountRow(
                                        account = account,
                                        currencySettings = state.settings,
                                        totalBalance = openTotalBalance,
                                        // With kind-split sections the kind is the header; only a
                                        // mixed list needs the per-row tag.
                                        onClick = { onAccountClick(account.id) },
                                    )
                                    if (index != accounts.lastIndex) {
                                        MoneySectionDivider()
                                    }
                                }
                            }
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
                    item {
                        MoneyCard(
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.animateItem(),
                        ) {
                            groups.hidden.forEachIndexed { index, account ->
                                AccountRow(
                                    account = account,
                                    currencySettings = state.settings,
                                    totalBalance = openTotalBalance,
                                    onClick = { onAccountClick(account.id) },
                                )
                                if (index != groups.hidden.lastIndex) {
                                    MoneySectionDivider()
                                }
                            }
                        }
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
                    MoneyCard(
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.animateItem(),
                    ) {
                        state.closedAccounts.forEachIndexed { index, account ->
                            AccountRow(
                                account = account,
                                currencySettings = state.settings,
                                totalBalance = openTotalBalance,
                                onClick = { onAccountClick(account.id) },
                            )
                            if (index != state.closedAccounts.lastIndex) {
                                MoneySectionDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountListItemUiModel,
    currencySettings: PortableSettings,
    totalBalance: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balanceText = formatInAppAmount(account.balance, currencySettings)
    // Empty for zero/negative balances or a non-positive total — a share is only meaningful
    // for money actually present.
    val statusText = when {
        account.requiresReopenAndSettle -> stringResource(R.string.account_status_reopen_settle)
        account.isClosed -> stringResource(R.string.account_status_closed)
        account.isHidden -> stringResource(R.string.account_status_hidden)
        account.isStale -> stringResource(R.string.account_status_stale)
        else -> null
    }
    val isDimmed = account.isClosed || account.isHidden
    val balanceStyle = if (balanceText.length > 18) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.titleMedium
    }
    val balanceSemantics = stringResource(R.string.account_balance_semantics_format, balanceText)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(account.name)
                    append(balanceSemantics)
                    statusText?.let { append("，$it") }
                }
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconBadge(
            iconName = account.iconName,
            colorName = account.colorName,
            isClosed = account.isClosed,
            size = 40.dp,
            iconSize = 22.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text = balanceText,
                style = balanceStyle,
                color = if (account.isClosed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )

        }
    }
}
