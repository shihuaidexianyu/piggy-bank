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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleArchiveVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasArchivedAccounts = state.archivedAccounts.isNotEmpty()

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "账户",
            trailing = {
                IconButton(
                    onClick = onCreateAccount,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "新建账户",
                        tint = Color.White,
                    )
                }
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.activeAccounts.isNotEmpty()) {
                item {
                    AccountOverviewCard(state = state)
                }
            }
            if (state.activeAccounts.isEmpty()) {
                item {
                    MoneyEmptyStateCard(
                        title = if (hasArchivedAccounts) "还没有活跃账户" else "还没有账户",
                        subtitle = if (hasArchivedAccounts) "归档账户可以在下方查看。" else "创建账户后，就能开始记录资金流和查看总资产。",
                    ) {
                        if (hasArchivedAccounts) {
                            OutlinedButton(onClick = onToggleArchiveVisibility) {
                                Text(if (state.showArchived) "收起已归档" else "查看已归档")
                            }
                        } else {
                            OutlinedButton(onClick = onCreateAccount) { Text("创建第一个账户") }
                        }
                    }
                }
            } else {
                val staleCount = state.activeAccounts.count { it.isStale }
                item {
                    MoneySectionHeader(
                        title = "活跃账户",
                        trailing = if (staleCount > 0) "$staleCount 个待核对" else "${state.activeAccounts.size} 个",
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.activeAccounts.forEach { account ->
                            AccountCard(
                                account = account,
                                currencySettings = state.settings,
                                onClick = { onAccountClick(account.id) },
                            )
                        }
                    }
                }
            }
            if (hasArchivedAccounts) {
                item {
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        MoneyListRow(
                            title = "已归档账户",
                            trailing = "${state.archivedAccounts.size} 个",
                            showChevron = false,
                            accessory = {
                                Text(
                                    text = if (state.showArchived) "收起" else "查看",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            },
                            modifier = Modifier.clickable(onClick = onToggleArchiveVisibility),
                        )
                    }
                }
            }
            if (state.showArchived) {
                item {
                    MoneySectionHeader(title = "已归档")
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.archivedAccounts.forEach { account ->
                            AccountCard(
                                account = account,
                                currencySettings = state.settings,
                                onClick = { onAccountClick(account.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountOverviewCard(state: AccountsUiState) {
    val totalAssets = state.activeAccounts.sumOf { it.balance }
    val staleCount = state.activeAccounts.count { it.isStale }
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
                    text = "账户总览",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = AmountFormatter.format(totalAssets, state.settings),
                    style = when {
                        totalAssets.toString().length > 10 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.headlineSmall
                    },
                    color = current,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            if (staleCount > 0) {
                MoneyStatusPill(
                    text = "$staleCount 个待核对",
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewMetric(
                label = "活跃",
                value = "${state.activeAccounts.size}",
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = "归档",
                value = "${state.archivedAccounts.size}",
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = "待核对",
                value = "$staleCount",
                accent = if (staleCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountListItemUiModel,
    currencySettings: AppSettings,
    onClick: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
    val balanceText = AmountFormatter.format(account.balance, currencySettings)
    val balanceStyle = when {
        balanceText.length > 18 -> MaterialTheme.typography.bodyMedium
        balanceText.length > 14 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountInitialBadge(
                name = account.name,
                isArchived = account.isArchived,
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
                    text = when {
                        account.isArchived -> "已归档"
                        account.isStale -> "余额待核对"
                        else -> "余额正常"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = balanceText,
                    style = balanceStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                if (account.isStale && !account.isArchived) {
                    MoneyStatusPill(
                        text = "待核对",
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountInitialBadge(
    name: String,
    isArchived: Boolean,
) {
    val accent = if (isArchived) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    val initial = accountInitial(name)
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
        )
    }
}

private fun accountInitial(name: String): String {
    return name.trim().firstOrNull()?.toString() ?: "账"
}
