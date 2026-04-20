package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartCashFlow: (CashFlowDirection, Long) -> Unit,
    onStartTransfer: () -> Unit,
    onStartUpdateBalance: (Long) -> Unit,
    onReminderClick: (DueReminderUiModel) -> Unit,
    onAllRemindersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerDirection by remember { mutableStateOf<CashFlowDirection?>(null) }
    var showUpdateBalancePicker by remember { mutableStateOf(false) }

    pickerDirection?.let { direction ->
        AccountPickerDialog(
            title = "选择${direction.displayName}账户",
            accounts = state.accountOptions,
            onDismiss = { pickerDirection = null },
            onPick = { accountId ->
                pickerDirection = null
                onStartCashFlow(direction, accountId)
            },
        )
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = "选择更新余额账户",
            accounts = state.accountOptions,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "首页",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                val assetChangeAccent = when {
                    state.periodAssetChange > 0 -> LocalMoneyColors.current.income
                    state.periodAssetChange < 0 -> LocalMoneyColors.current.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                TotalAssetsBlock(
                    totalAssets = AmountFormatter.format(state.totalAssets, state.settings),
                    assetChangeLabel = "${state.settings.homePeriod.displayName}资产变化",
                    assetChange = formatSignedAmount(state.periodAssetChange, state.settings),
                    assetChangeAccent = assetChangeAccent,
                    accountCount = state.accountOptions.size,
                    staleCount = state.staleAccountCount,
                    showStaleMark = state.settings.showStaleMark,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowPill(
                        label = "${state.settings.homePeriod.displayName}净流入",
                        value = AmountFormatter.format(state.periodNetInflow, state.settings),
                        accent = LocalMoneyColors.current.income,
                        modifier = Modifier.weight(1f),
                    )
                    FlowPill(
                        label = "${state.settings.homePeriod.displayName}净流出",
                        value = AmountFormatter.format(state.periodNetOutflow, state.settings),
                        accent = LocalMoneyColors.current.expense,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.dueReminders.isNotEmpty()) {
                item {
                    MoneySectionHeader(
                        title = "待处理提醒",
                        trailingContent = {
                            Text(
                                text = "管理全部",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onAllRemindersClick),
                            )
                        },
                    )
                }
                items(state.dueReminders) { reminder ->
                    MoneyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReminderClick(reminder) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = reminder.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    MoneyStatusPill(
                                        text = when (reminder.type) {
                                            ReminderType.MANUAL -> "待缴费"
                                            ReminderType.SUBSCRIPTION -> "待确认"
                                        },
                                        accent = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Text(
                                    text = "轻点处理这条提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = reminder.amountFormatted,
                                style = MaterialTheme.typography.titleLarge,
                                color = LocalMoneyColors.current.current,
                            )
                        }
                    }
                }
            }
            item {
                MoneySectionHeader(title = "快速记录")
            }
            item {
                ActionGrid(
                    onInflow = { pickerDirection = CashFlowDirection.INFLOW },
                    onOutflow = { pickerDirection = CashFlowDirection.OUTFLOW },
                    onTransfer = onStartTransfer,
                    onUpdateBalance = { showUpdateBalancePicker = true },
                    onReminders = onAllRemindersClick,
                    enabled = state.accountOptions.isNotEmpty(),
                    transferEnabled = state.accountOptions.size >= 2,
                )
            }
        }
    }
}

@Composable
private fun TotalAssetsBlock(
    totalAssets: String,
    assetChangeLabel: String,
    assetChange: String,
    assetChangeAccent: Color,
    accountCount: Int,
    staleCount: Int,
    showStaleMark: Boolean,
) {
    val borderColor = if (staleCount > 0 && showStaleMark) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(28.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "总资产",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = totalAssets,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = assetChangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = assetChange,
                    style = MaterialTheme.typography.titleMedium,
                    color = assetChangeAccent,
                )
            }
            val statusText = if (staleCount > 0 && showStaleMark) {
                "$staleCount 个账户待更新"
            } else {
                "$accountCount 个账户"
            }
            MoneyStatusPill(
                text = statusText,
                accent = if (staleCount > 0 && showStaleMark) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (staleCount > 0 && showStaleMark) {
                    "先处理待更新账户，再记账会更准确。"
                } else {
                    "近期收支会实时反映在总资产里。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSignedAmount(amount: Long, settings: AppSettings): String {
    return when {
        amount > 0 -> "+${AmountFormatter.format(amount, settings)}"
        else -> AmountFormatter.format(amount, settings)
    }
}

@Composable
private fun FlowPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = accent,
                        shape = CircleShape,
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
}

@Composable
private fun ActionGrid(
    onInflow: () -> Unit,
    onOutflow: () -> Unit,
    onTransfer: () -> Unit,
    onUpdateBalance: () -> Unit,
    onReminders: () -> Unit,
    enabled: Boolean,
    transferEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                label = "入账",
                icon = { Icon(Icons.Outlined.SouthWest, contentDescription = null) },
                tint = LocalMoneyColors.current.income,
                bgColor = LocalMoneyColors.current.income.copy(alpha = 0.08f),
                onClick = onInflow,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "出账",
                icon = { Icon(Icons.Outlined.NorthEast, contentDescription = null) },
                tint = LocalMoneyColors.current.expense,
                bgColor = LocalMoneyColors.current.expense.copy(alpha = 0.08f),
                onClick = onOutflow,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                label = "转账",
                icon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                tint = LocalMoneyColors.current.transfer,
                bgColor = LocalMoneyColors.current.transfer.copy(alpha = 0.08f),
                onClick = onTransfer,
                enabled = transferEnabled,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "更新余额",
                icon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                tint = LocalMoneyColors.current.current,
                bgColor = LocalMoneyColors.current.current.copy(alpha = 0.08f),
                onClick = onUpdateBalance,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        ActionTile(
            label = "定期提醒",
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            tint = LocalMoneyColors.current.reminder,
            bgColor = LocalMoneyColors.current.reminder.copy(alpha = 0.08f),
            onClick = onReminders,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: @Composable () -> Unit,
    tint: Color,
    bgColor: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = bgColor,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides tint,
                ) {
                    icon()
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
