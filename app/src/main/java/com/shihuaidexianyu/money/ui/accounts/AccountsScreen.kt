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
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors

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
                IconButton(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.accounts_create),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = MoneyDimens.bottomNavContentPadding),
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
                        MoneyTonalButton(onClick = onCreateAccount) {
                            Text(stringResource(R.string.accounts_create_first))
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
                                        null -> if (accounts.firstOrNull()?.kind == AccountKind.INVESTMENT) {
                                            R.string.account_kind_investment
                                        } else {
                                            R.string.account_kind_funding
                                        }
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
                                                modifier = Modifier.size(48.dp),
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
    val shareText = formatSharePercent(account.balance, totalBalance)
    val statusText = when {
        account.requiresReopenAndSettle -> stringResource(R.string.account_status_reopen_settle)
        account.isClosed -> stringResource(R.string.account_status_closed)
        account.isHidden -> stringResource(R.string.account_status_hidden)
        account.isStale -> stringResource(R.string.account_status_stale)
        else -> null
    }
    // Second line for healthy rows: the account's signed net change this calendar month,
    // colored by direction. The "本月" window is stated once in the overview caption above the
    // list rather than repeated on every row; TalkBack still hears the full sentence.
    // Zero (a quiet month, or perfectly offsetting moves) stays silent.
    val monthChangeText = if (account.monthNetChange != 0L) {
        signedFormatInAppAmount(account.monthNetChange, currencySettings)
    } else {
        null
    }
    val monthChangeSemantics = monthChangeText?.let {
        stringResource(R.string.account_month_change_format, it)
    }
    val caption = statusText ?: monthChangeText
    val captionSemantics = statusText ?: monthChangeSemantics
    val captionColor = when {
        statusText != null -> MaterialTheme.colorScheme.onSurfaceVariant
        account.monthNetChange > 0L -> LocalMoneyColors.current.income
        account.monthNetChange < 0L -> LocalMoneyColors.current.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isDimmed = account.isClosed || account.isHidden
    val balanceStyle = if (balanceText.length > 18) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.titleMedium
    }
    val balanceSemantics = stringResource(R.string.account_balance_semantics_format, balanceText)
    val shareSemantics = if (shareText.isNotEmpty()) {
        stringResource(R.string.account_share_semantics_format, shareText)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(account.name)
                    append(balanceSemantics)
                    captionSemantics?.let { append("，$it") }
                    shareSemantics?.let { append("，$it") }
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
            // The ring visualizes the same share printed at the row's trailing edge. Every
            // open account gets one (a zero balance draws the bare track) so badge sizes —
            // and therefore name alignment — stay uniform down the list.
            shareFraction = if (!account.isClosed && totalBalance > 0L) {
                (account.balance.toDouble() / totalBalance.toDouble()).toFloat()
            } else {
                null
            },
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
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = captionColor,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
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
            if (shareText.isNotEmpty()) {
                Text(
                    text = shareText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Page-level summary limited to what the home dashboard does not already show: open and stale
 * account counts. The total-assets amount and the funding/investment split live on home only,
 * so each piece of information keeps a single source. This is also where the per-row change
 * stat's calendar-month window is stated — once, instead of on every row.
 */
@Composable
private fun AccountsOverviewCard(
    openCount: Int,
    staleCount: Int,
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
        )
        Text(
            text = stringResource(R.string.accounts_month_change_note),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
