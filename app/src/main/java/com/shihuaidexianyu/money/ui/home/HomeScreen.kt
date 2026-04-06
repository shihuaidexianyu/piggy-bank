package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyMetricTile
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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(title = "首页")
        }
        item {
            MoneyCard {
                Text("总资产", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = AmountFormatter.format(state.totalAssets, state.settings),
                    style = MaterialTheme.typography.displayLarge,
                )
                val statusText = if (state.staleAccountCount > 0 && state.settings.showStaleMark) {
                    "${state.staleAccountCount} 个账户待更新"
                } else {
                    "${state.accountOptions.size} 个账户"
                }
                MoneyStatusPill(
                    text = statusText,
                    accent = if (state.staleAccountCount > 0 && state.settings.showStaleMark) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyMetricTile(
                    label = "${state.settings.homePeriod.displayName}净流入",
                    value = AmountFormatter.format(state.periodNetInflow, state.settings),
                    modifier = Modifier.weight(1f),
                    accent = LocalMoneyColors.current.income,
                )
                MoneyMetricTile(
                    label = "${state.settings.homePeriod.displayName}净流出",
                    value = AmountFormatter.format(state.periodNetOutflow, state.settings),
                    modifier = Modifier.weight(1f),
                    accent = LocalMoneyColors.current.expense,
                )
            }
        }
        if (state.dueReminders.isNotEmpty()) {
            item {
                MoneySectionHeader(title = "待处理提醒")
            }
            item {
                MoneyCard {
                    state.dueReminders.forEach { reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReminderClick(reminder) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            }
                            Text(
                                text = reminder.amountFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = "管理全部提醒 >",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onAllRemindersClick),
                    )
                }
            }
        }
        item {
            MoneySectionHeader(title = "操作")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionButton(
                    label = "入账",
                    icon = { Icon(Icons.Outlined.SouthWest, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { pickerDirection = CashFlowDirection.INFLOW },
                    enabled = state.accountOptions.isNotEmpty(),
                )
                HomeActionButton(
                    label = "出账",
                    icon = { Icon(Icons.Outlined.NorthEast, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { pickerDirection = CashFlowDirection.OUTFLOW },
                    enabled = state.accountOptions.isNotEmpty(),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionButton(
                    label = "转账",
                    icon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = onStartTransfer,
                    enabled = state.accountOptions.size >= 2,
                )
                HomeActionButton(
                    label = "更新余额",
                    icon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { showUpdateBalancePicker = true },
                    enabled = state.accountOptions.isNotEmpty(),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionButton(
                    label = "定期提醒",
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = onAllRemindersClick,
                    enabled = true,
                )
            }
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        icon()
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

