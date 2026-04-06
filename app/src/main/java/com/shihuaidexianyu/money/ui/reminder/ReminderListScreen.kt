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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill

@Composable
fun ReminderListScreen(
    state: ReminderListUiState,
    onCreateReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
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
                Icon(Icons.Outlined.Add, contentDescription = "添加提醒")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MoneyPageTitle(title = "定期提醒")
            }
            if (!state.isLoading && state.reminders.isEmpty()) {
                item {
                    Text(
                        text = "暂无提醒，点击右下角添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(state.reminders, key = { it.id }) { reminder ->
                ReminderListItem(
                    reminder = reminder,
                    onClick = { onEditReminder(reminder.id) },
                    onDelete = { deleteTarget = reminder.id },
                )
            }
        }
    }
}

@Composable
private fun ReminderListItem(
    reminder: ReminderUiModel,
    onClick: () -> Unit,
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
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
