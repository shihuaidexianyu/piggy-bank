package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
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
    val groupedActiveAccounts = state.activeAccounts.groupBy { it.groupType }
    val groupedArchivedAccounts = state.archivedAccounts.groupBy { it.groupType }
    val hasArchivedAccounts = state.archivedAccounts.isNotEmpty()

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "账户",
            trailing = {
                OutlinedButton(onClick = onCreateAccount) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("新建账户", modifier = Modifier.padding(start = 6.dp))
                }
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
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
                state.settings.accountGroupOrder.forEach { groupType ->
                    val accounts = groupedActiveAccounts[groupType].orEmpty()
                    if (accounts.isEmpty()) return@forEach
                    item {
                        MoneySectionHeader(title = groupType.displayName)
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            accounts.forEach { account ->
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
                state.settings.accountGroupOrder.forEach { groupType ->
                    val accounts = groupedArchivedAccounts[groupType].orEmpty()
                    if (accounts.isEmpty()) return@forEach
                    item {
                        MoneySectionHeader(title = "${groupType.displayName} · 已归档")
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            accounts.forEach { account ->
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
}

@Composable
private fun AccountCard(
    account: AccountListItemUiModel,
    currencySettings: AppSettings,
    onClick: () -> Unit,
) {
    val (icon, iconBg, iconTint) = accountGroupVisuals(account.groupType)
    val statusLabel = when {
        account.isArchived -> "已归档"
        account.isStale -> "待更新"
        else -> account.groupType.displayName
    }
    val statusColor = when {
        account.isArchived -> MaterialTheme.colorScheme.outline
        account.isStale -> LocalMoneyColors.current.current
        else -> iconTint
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = iconBg, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }
            Text(
                text = AmountFormatter.format(account.balance, currencySettings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

private fun accountGroupVisuals(groupType: AccountGroupType): Triple<ImageVector, Color, Color> {
    return when (groupType) {
        AccountGroupType.PAYMENT -> Triple(
            Icons.Outlined.AccountBalanceWallet,
            Color(0xFFFFF3D6),
            Color(0xFFC4943A),
        )
        AccountGroupType.BANK -> Triple(
            Icons.Outlined.AccountBalance,
            Color(0xFFE3F2FD),
            Color(0xFF5B8DB8),
        )
        AccountGroupType.INVESTMENT -> Triple(
            Icons.AutoMirrored.Outlined.TrendingUp,
            Color(0xFFE8F5E9),
            Color(0xFF5A8A6E),
        )
    }
}
