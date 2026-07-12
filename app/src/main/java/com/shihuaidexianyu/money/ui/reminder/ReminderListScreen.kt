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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
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
import kotlinx.coroutines.flow.Flow

@Composable
fun ReminderListScreen(
    state: ReminderListUiState,
    onCreateReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onProcessReminder: (ReminderUiModel) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onSkipReminder: (Long, Long) -> Unit,
    onPendingSkipEnqueued: (String) -> Unit,
    effects: Flow<ReminderListEffect>,
    notificationPermissionState: NotificationPermissionUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: (NotificationSettingsTarget) -> Unit,
    onUpdateBalance: (Long) -> Unit,
    onBatchReconcile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    val skippedMessage = stringResource(R.string.reminder_skipped)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is ReminderListEffect.ShowMessage) {
                rootDispatcher?.dispatch(rootSnackbarEffect(effect.message))
            }
        }
    }

    state.pendingSkip?.let { pending ->
        LaunchedEffect(pending.token) {
            rootDispatcher?.dispatch(
                rootSnackbarEffect(
                    skippedMessage,
                    undoLabel,
                    RootSnackbarAction.UndoReminderSkip(pending.undoToken),
                    pending.token,
                ),
            )
            onPendingSkipEnqueued(pending.token)
        }
    }

    deleteTarget?.let { id ->
        MoneyConfirmDialog(
            title = stringResource(R.string.reminder_delete_title),
            message = stringResource(R.string.reminder_delete_message),
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
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.reminder_add))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = modifier.padding(innerPadding),
        ) {
            MoneyPageTitle(
                title = stringResource(R.string.reminder_center_title),
                leading = { MoneyBackButton(onClick = onBack) },
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (notificationPermissionState != NotificationPermissionUiState.Granted) {
                    item {
                        NotificationPermissionStatusCard(
                            state = notificationPermissionState,
                            onRequest = onRequestNotificationPermission,
                            onOpenSettings = onOpenNotificationSettings,
                        )
                    }
                }
                if (!state.isLoading && state.balanceReminders.isEmpty() &&
                    state.dueReminders.isEmpty() && state.upcomingReminders.isEmpty() &&
                    state.pausedReminders.isEmpty()
                ) {
                    item {
                        MoneyEmptyStateCard(
                            title = stringResource(R.string.reminder_empty),
                            subtitle = stringResource(R.string.reminder_empty_description),
                            action = {
                                Button(
                                    onClick = onCreateReminder,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.reminder_add_recurring))
                                }
                            },
                        )
                    }
                }
                if (state.balanceReminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.reminder_balance_due),
                            trailing = pluralStringResource(
                                R.plurals.account_count,
                                state.balanceReminders.size,
                                state.balanceReminders.size,
                            ),
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
                if (state.dueReminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.reminder_due),
                            trailing = pluralStringResource(
                                R.plurals.reminder_count,
                                state.dueReminders.size,
                                state.dueReminders.size,
                            ),
                        )
                    }
                }
                items(state.dueReminders, key = { "due:${it.id}" }) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        isDue = true,
                        onProcess = { onProcessReminder(reminder) },
                        onSkip = { onSkipReminder(reminder.id, reminder.nextDueAt) },
                        onEdit = { onEditReminder(reminder.id) },
                        onDelete = { deleteTarget = reminder.id },
                    )
                }
                if (state.upcomingReminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.reminder_upcoming),
                            trailing = pluralStringResource(
                                R.plurals.reminder_count,
                                state.upcomingReminders.size,
                                state.upcomingReminders.size,
                            ),
                        )
                    }
                }
                items(state.upcomingReminders, key = { "upcoming:${it.id}" }) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        isDue = false,
                        onProcess = {},
                        onSkip = {},
                        onEdit = { onEditReminder(reminder.id) },
                        onDelete = { deleteTarget = reminder.id },
                    )
                }
                if (state.pausedReminders.isNotEmpty()) {
                    item {
                        MoneySectionHeader(
                            title = stringResource(R.string.reminder_paused),
                            trailing = pluralStringResource(
                                R.plurals.reminder_count,
                                state.pausedReminders.size,
                                state.pausedReminders.size,
                            ),
                        )
                    }
                }
                items(state.pausedReminders, key = { "paused:${it.id}" }) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        isDue = false,
                        onProcess = {},
                        onSkip = {},
                        onEdit = { onEditReminder(reminder.id) },
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
                Text(stringResource(R.string.reminder_batch_confirm_unchanged), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.reminder_batch_confirm_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.reminder_go_reconcile),
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
                    text = stringResource(R.string.account_stale_badge),
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
                text = stringResource(R.string.account_detail_kind_reconciliation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReminderListItem(
    reminder: ReminderUiModel,
    isDue: Boolean,
    onProcess: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoneyCard(
        modifier = modifier.clickable(onClick = onEdit),
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
                            ReminderType.MANUAL -> stringResource(R.string.reminder_type_manual)
                            ReminderType.SUBSCRIPTION -> stringResource(R.string.reminder_type_subscription)
                        },
                        accent = when (reminder.type) {
                            ReminderType.MANUAL -> MaterialTheme.colorScheme.primary
                            ReminderType.SUBSCRIPTION -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
                Text(
                    text = stringResource(
                        R.string.reminder_amount_period_format,
                        reminder.amountFormatted,
                        reminder.periodDescription,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (reminder.isOverdue) {
                        stringResource(R.string.reminder_due)
                    } else {
                        stringResource(R.string.reminder_next_due_format, reminder.nextDueFormatted)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminder.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!reminder.isEnabled) {
                    Text(
                        text = stringResource(R.string.reminder_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isDue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onProcess) {
                    Text(stringResource(R.string.reminder_record))
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.reminder_skip_period))
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.action_edit))
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionStatusCard(
    state: NotificationPermissionUiState,
    onRequest: () -> Unit,
    onOpenSettings: (NotificationSettingsTarget) -> Unit,
) {
    val title: String
    val message: String
    val actionLabel: String
    val action: () -> Unit
    when (state) {
        NotificationPermissionUiState.Granted -> return
        NotificationPermissionUiState.NotRequested -> {
            title = stringResource(R.string.notification_enable_title)
            message = stringResource(R.string.notification_enable_message)
            actionLabel = stringResource(R.string.notification_allow)
            action = onRequest
        }
        is NotificationPermissionUiState.Denied -> if (state.canRequestAgain) {
            title = stringResource(R.string.notification_denied_title)
            message = stringResource(R.string.notification_denied_message)
            actionLabel = stringResource(R.string.notification_request_again)
            action = onRequest
        } else {
            title = stringResource(R.string.notification_denied_permanent_title)
            message = stringResource(R.string.notification_denied_permanent_message)
            actionLabel = stringResource(R.string.notification_open_settings)
            action = { onOpenSettings(NotificationSettingsTarget.APPLICATION) }
        }
        is NotificationPermissionUiState.SettingsRequired -> {
            title = when (state.target) {
                NotificationSettingsTarget.APPLICATION -> stringResource(R.string.settings_app_notifications_disabled)
                NotificationSettingsTarget.RECURRING_CHANNEL -> stringResource(R.string.notification_recurring_channel_disabled)
                NotificationSettingsTarget.BALANCE_CHANNEL -> stringResource(R.string.notification_balance_channel_disabled)
            }
            message = stringResource(R.string.notification_settings_required_message)
            actionLabel = stringResource(R.string.notification_open_settings)
            action = { onOpenSettings(state.target) }
        }
    }
    MoneyCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = action) { Text(actionLabel) }
        }
    }
}
