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
import androidx.compose.material3.LinearProgressIndicator
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
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.common.accountVisualColor
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onCreateAccount: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onToggleArchiveVisibility: () -> Unit,
    onCreateSavingsGoal: () -> Unit,
    onSavingsGoalClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasArchivedAccounts = state.archivedAccounts.isNotEmpty()
    val positiveAssetsTotal = state.activeAccounts.sumOf { account ->
        if (account.balance > 0) account.balance else 0L
    }

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
            if (state.savingsGoals.isNotEmpty() || state.activeAccounts.isNotEmpty()) {
                item {
                    SavingsGoalsRow(
                        goals = state.savingsGoals,
                        settings = state.settings,
                        onCreateGoal = onCreateSavingsGoal,
                        onGoalClick = onSavingsGoalClick,
                    )
                }
            }
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
                itemsIndexed(state.activeAccounts, key = { _, account -> account.id }) { _, account ->
                    AccountCard(
                        account = account,
                        currencySettings = state.settings,
                        positiveAssetsTotal = positiveAssetsTotal,
                        onClick = { onAccountClick(account.id) },
                    )
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
                itemsIndexed(state.archivedAccounts, key = { _, account -> account.id }) { _, account ->
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
                    text = "总资产",
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
        if (staleCount > 0) {
            Text(
                text = "有账户余额需要确认，更新后总资产会更准确。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountListItemUiModel,
    currencySettings: AppSettings,
    positiveAssetsTotal: Long,
    onClick: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
    val balanceText = AmountFormatter.format(account.balance, currencySettings)
    val showAssetShare = !account.isArchived && account.balance > 0 && positiveAssetsTotal > 0
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
        account.isArchived -> "已归档"
        account.isStale -> "余额待核对"
        else -> "余额正常"
    }
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
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(account.name)
                    append("，余额 ${balanceText}")
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

@Composable
private fun SavingsGoalsRow(
    goals: List<SavingsGoalUiModel>,
    settings: AppSettings,
    onCreateGoal: () -> Unit,
    onGoalClick: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoneySectionHeader(title = "储蓄目标")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(goals, key = { it.id }) { goal ->
                SavingsGoalCard(
                    goal = goal,
                    settings = settings,
                    onClick = { onGoalClick(goal.id) },
                )
            }
            item {
                AddSavingsGoalCard(onClick = onCreateGoal)
            }
        }
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoalUiModel,
    settings: AppSettings,
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val currentText = AmountFormatter.format(goal.currentAmount, settings)
    val targetText = AmountFormatter.format(goal.targetAmount, settings)
    val percentage = if (goal.targetAmount > 0L) {
        ((goal.currentAmount * 100L) / goal.targetAmount).coerceIn(0L, 100L)
    } else {
        0L
    }
    val percentageText = if (percentage < 1L && goal.currentAmount > 0L) "<1%" else "$percentage%"
    @Suppress("FloatingPointUsageInMoney")
    val progressFraction = (percentage / 100f).coerceIn(0f, 1f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
    val balanceStyle = when {
        currentText.length > 18 -> MaterialTheme.typography.bodyMedium
        currentText.length > 14 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }
    val statusText = if (goal.isAchieved) "已达成" else "进度 $percentageText"

    Surface(
        modifier = Modifier
            .width(200.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "储蓄目标 ${goal.name}，已存 $currentText，目标 $targetText，进度 $percentageText"
                role = Role.Button
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = accentColor.copy(alpha = 0.10f),
                        size = Size(
                            width = size.width * progressFraction.coerceIn(0.01f, 1f),
                            height = size.height,
                        ),
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = goal.name,
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
                    Text(
                        text = percentageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (goal.isAchieved) MaterialTheme.colorScheme.tertiary else accentColor,
                        maxLines = 1,
                    )
                    Text(
                        text = currentText,
                        style = balanceStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSavingsGoalCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f),
        ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "添加目标",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
