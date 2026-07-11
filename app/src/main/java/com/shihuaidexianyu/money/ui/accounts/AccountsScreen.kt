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
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
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
    onManageAccountOrder: () -> Unit,
    onManageSavingsGoal: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val groups = accountGroups(state.openAccounts, state.closedAccounts)
    val hasClosedAccounts = state.closedAccounts.isNotEmpty()
    val positiveAssetsTotal = (state.openAccounts + state.closedAccounts)
        .mapNotNull { account -> account.balance.takeIf { it > 0L } }
        .ledgerSumExact()

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
                MoneySectionHeader(title = "管理")
            }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = "账户顺序",
                        subtitle = "调整正常与隐藏账户的显示顺序",
                        modifier = Modifier.clickable(onClick = onManageAccountOrder),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "净资产目标",
                        subtitle = if (state.savingsGoal == null) "设置单一净资产目标" else "查看或修改净资产目标",
                        modifier = Modifier.clickable(onClick = onManageSavingsGoal),
                    )
                }
            }
            if (state.openAccounts.isEmpty()) {
                item {
                    MoneyEmptyStateCard(
                        title = if (hasClosedAccounts) "还没有开放账户" else "还没有账户",
                        subtitle = if (hasClosedAccounts) "关闭账户可以在下方查看。" else "创建账户后，就能开始记录资金流和查看总资产。",
                    ) {
                        if (hasClosedAccounts) {
                            OutlinedButton(onClick = onToggleClosedVisibility) {
                                Text(if (state.showClosed) "收起已关闭" else "查看已关闭")
                            }
                        } else {
                            OutlinedButton(onClick = onCreateAccount) { Text("创建第一个账户") }
                        }
                    }
                }
            } else {
                if (groups.normal.isNotEmpty()) {
                    val staleCount = groups.normal.count { it.isStale }
                    item {
                        MoneySectionHeader(
                            title = "正常账户",
                            trailing = if (staleCount > 0) "$staleCount 个待核对" else "${groups.normal.size} 个",
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
                    item { MoneySectionHeader(title = "隐藏账户", trailing = "${groups.hidden.size} 个") }
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
                            title = "已关闭账户",
                            trailing = "${state.closedAccounts.size} 个",
                            showChevron = false,
                            accessory = {
                                Text(
                                    text = if (state.showClosed) "收起" else "查看",
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
                    MoneySectionHeader(title = "已关闭账户")
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
    val goal = state.savingsGoal
    val goalProgress = goal?.let {
        netWorthGoalProgressPresentation(it.currentAmount, it.targetAmount)
    }
    val goalText = if (goal != null) {
        if (goal.isAchieved) {
            "已达成净资产目标 · 当前 ${formatInAppAmount(goal.currentAmount, state.settings)} · " +
                "进度 ${requireNotNull(goalProgress).percentageText}"
        } else {
            "当前 ${formatInAppAmount(goal.currentAmount, state.settings)} · " +
                "目标 ${formatInAppAmount(goal.targetAmount, state.settings)} · " +
                "进度 ${requireNotNull(goalProgress).percentageText}"
        }
    } else {
        null
    }
    @Suppress("FloatingPointUsageInMoney")
    val progressFraction = (goalProgress?.geometryPercent ?: 0) / 100f
    val accentColor = MaterialTheme.colorScheme.primary

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
                    text = formatInAppAmount(totalAssets, state.settings),
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
        if (goalText != null) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind {
                        if (progressFraction > 0f) {
                            drawRect(
                                color = accentColor.copy(alpha = 0.10f),
                                size = Size(
                                    width = size.width * progressFraction.coerceIn(0.01f, 1f),
                                    height = size.height,
                                ),
                            )
                        }
                    },
            ) {
                Text(
                    text = goalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (goal?.isAchieved == true) MaterialTheme.colorScheme.tertiary else accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        account.requiresReopenAndSettle -> "需重新开启并结清"
        account.isClosed -> "已关闭"
        account.isHidden -> "已隐藏 · 仍计入净资产"
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
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    if (account.isStale && !account.isClosed) {
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

