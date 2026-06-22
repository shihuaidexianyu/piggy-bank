package com.shihuaidexianyu.money.ui.reminder

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyBackButton
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill

@Composable
fun ReminderListScreen(
    state: ReminderListUiState,
    onCreateReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onProcessReminder: (ReminderUiModel) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onConfirmReminder: (Long) -> Unit,
    onUpdateBalance: (Long) -> Unit,
    onBatchReconcile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    deleteTarget?.let { id ->
        MoneyConfirmDialog(
            title = "删除提醒",
            message = "确定要删除这个提醒吗？",
            onConfirm = {
                onDeleteReminder(id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateReminder) {
                Icon(Icons.Rounded.Add, contentDescription = "添加提醒")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier.padding(innerPadding),
        ) {
            MoneyPageTitle(
                title = "提醒中心",
                leading = { MoneyBackButton(onClick = onBack) },
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isLoading && state.balanceReminders.isEmpty() && state.reminders.isEmpty()) {
                    item {
                        MoneyEmptyStateCard(
                            title = "暂无待处理提醒",
                            subtitle = "余额核对和定期提醒都会显示在这里。",
                            action = {
                                Button(
                                    onClick = onCreateReminder,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("添加定期提醒")
                                }
                            },
                        )
                    }
                }
                if (state.balanceReminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = "余额待核对",
                            trailing = "${state.balanceReminders.size} 个账户",
                        )
                    }
                    item {
                        BalanceReminderSection(
                            reminders = state.balanceReminders,
                            onUpdateBalance = onUpdateBalance,
                            onBatchReconcile = onBatchReconcile,
                        )
                    }
                }
                if (state.reminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = "定期提醒",
                            trailing = "${state.reminders.size} 条",
                        )
                    }
                }
                items(state.reminders, key = { it.id }) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        onClick = {
                            if (reminder.isOverdue && reminder.isEnabled) {
                                onProcessReminder(reminder)
                            } else {
                                onEditReminder(reminder.id)
                            }
                        },
                        onConfirmOnly = { onConfirmReminder(reminder.id) },
                        onDelete = { deleteTarget = reminder.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceReminderSection(
    reminders: List<BalanceReminderUiModel>,
    onUpdateBalance: (Long) -> Unit,
    onBatchReconcile: () -> Unit,
) {
    MoneyListSection {
        reminders.forEach { reminder ->
            BalanceReminderRow(
                reminder = reminder,
                onClick = { onUpdateBalance(reminder.accountId) },
            )
            MoneySectionDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBatchReconcile)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("批量确认无变化", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "适合余额没有实际变化的账户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "去核对",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BalanceReminderRow(
    reminder: BalanceReminderUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                MoneyStatusPill(
                    text = "待核对",
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = reminder.lastBalanceUpdateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = reminder.currentBalanceFormatted,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = "核对",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReminderListItem(
    reminder: ReminderUiModel,
    onClick: () -> Unit,
    onConfirmOnly: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoneyCard(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reminder.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    MoneyStatusPill(
                        text = when (reminder.type) {
                            ReminderType.MANUAL -> "手动缴费"
                            ReminderType.SUBSCRIPTION -> "自动扣费"
                        },
                        accent = when (reminder.type) {
                            ReminderType.MANUAL -> MaterialTheme.colorScheme.primary
                            ReminderType.SUBSCRIPTION -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
                Text(
                    text = "${reminder.amountFormatted} · ${reminder.periodDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (reminder.isOverdue) "已到期" else "下次: ${reminder.nextDueFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminder.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!reminder.isEnabled) {
                    Text(
                        text = "已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (reminder.isOverdue && reminder.isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClick) {
                    Text("生成记录")
                }
                TextButton(onClick = onConfirmOnly) {
                    Text("仅确认")
                }
            }
        }
    }
}
