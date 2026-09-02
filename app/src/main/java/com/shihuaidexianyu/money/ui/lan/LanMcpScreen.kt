package com.shihuaidexianyu.money.ui.lan

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalStatus
import com.shihuaidexianyu.money.lan.LanPairedDevice
import com.shihuaidexianyu.money.lan.MoneyLanServerStatus
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
fun LanMcpScreen(
    state: LanMcpUiState,
    effectFlow: SharedFlow<LanMcpEffect>,
    onBack: () -> Unit,
    onAllowWriteChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onApprovePairing: () -> Unit,
    onDenyPairing: () -> Unit,
    onRevokeDevice: (String) -> Unit,
    onUndoLatest: () -> Unit,
    onDiscardLatest: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val copyText: (String) -> Unit = { value ->
        clipboardScope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("Money", value).toClipEntry())
        }
    }
    var discardTarget by remember { mutableStateOf<AiMutationJournalEntry?>(null) }
    var revokeTarget by remember { mutableStateOf<LanPairedDevice?>(null) }
    var manualPairingExpanded by remember { mutableStateOf(false) }
    CollectUiEffects(effectFlow, snackbarHostState) { }

    // Confirmation-style pairing (session.device.v1): the client presented itself and is polling;
    // the user approves here or from the notification actions.
    state.runtime.pendingPairRequestId?.let {
        MoneyConfirmDialog(
            title = stringResource(R.string.lan_pair_request_title),
            message = stringResource(
                R.string.lan_pair_request_message,
                state.runtime.pendingPairClientName ?: "",
            ),
            confirmLabel = stringResource(R.string.lan_notification_approve),
            dismissLabel = stringResource(R.string.lan_notification_deny),
            onConfirm = onApprovePairing,
            onDismiss = onDenyPairing,
        )
    }

    discardTarget?.let { entry ->
        MoneyConfirmDialog(
            title = stringResource(R.string.lan_discard_title),
            message = stringResource(R.string.lan_discard_message, entry.summary),
            confirmLabel = stringResource(R.string.lan_discard_confirm),
            destructive = true,
            onConfirm = {
                onDiscardLatest(entry.id)
                discardTarget = null
            },
            onDismiss = { discardTarget = null },
        )
    }

    revokeTarget?.let { device ->
        MoneyConfirmDialog(
            title = stringResource(R.string.lan_device_revoke_title),
            message = stringResource(R.string.lan_device_revoke_message, device.clientName),
            confirmLabel = stringResource(R.string.lan_device_revoke),
            destructive = true,
            onConfirm = {
                onRevokeDevice(device.deviceId)
                revokeTarget = null
            },
            onDismiss = { revokeTarget = null },
        )
    }

    MoneyFormPage(
        title = stringResource(R.string.lan_screen_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        item {
            MoneySectionHeader(title = stringResource(R.string.lan_section_service))
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.lan_current_status),
                    subtitle = statusDescription(state),
                    showChevron = false,
                    leading = {
                        Icon(Icons.Rounded.Lan, contentDescription = null)
                    },
                    accessory = {
                        MoneyStatusPill(
                            text = statusLabel(state.runtime.status),
                            accent = when (state.runtime.status) {
                                MoneyLanServerStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                MoneyLanServerStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.lan_allow_write),
                    subtitle = stringResource(R.string.lan_allow_write_description),
                    showChevron = false,
                    switchChecked = if (state.isRunning) state.runtime.allowWrite else state.allowWriteDraft,
                    enabled = !state.isRunning,
                    onClick = if (state.isRunning) null else {
                        { onAllowWriteChange(!state.allowWriteDraft) }
                    },
                    accessory = {
                        Switch(
                            checked = if (state.isRunning) state.runtime.allowWrite else state.allowWriteDraft,
                            onCheckedChange = if (state.isRunning) null else onAllowWriteChange,
                            enabled = !state.isRunning,
                        )
                    },
                )
                MoneySectionDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isRunning) {
                        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.lan_stop_service))
                        }
                    } else {
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.lan_start_session))
                        }
                    }
                }
            }
        }

        if (state.runtime.status == MoneyLanServerStatus.RUNNING) {
            item { MoneySectionHeader(title = stringResource(R.string.lan_section_connection)) }
            item {
                MoneyListSection {
                    val endpoint = state.runtime.addresses.firstOrNull()?.let { address ->
                        "$address:${state.runtime.port}"
                    } ?: stringResource(R.string.lan_endpoint_unavailable, state.runtime.port ?: "—")
                    MoneyListRow(
                        title = stringResource(R.string.lan_endpoint),
                        subtitle = endpoint,
                        trailing = stringResource(R.string.lan_copy),
                        onClick = { copyText(endpoint) },
                        showChevron = false,
                    )
                    state.runtime.discoveryName?.let {
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.lan_discovery_active),
                            subtitle = it,
                            showChevron = false,
                        )
                    }
                    state.runtime.pairingCode?.let { code ->
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.lan_manual_pairing),
                            subtitle = if (manualPairingExpanded) {
                                code.chunked(4).joinToString(" ")
                            } else {
                                stringResource(R.string.lan_manual_pairing_description)
                            },
                            trailing = if (manualPairingExpanded) {
                                stringResource(R.string.lan_copy_command)
                            } else {
                                null
                            },
                            onClick = {
                                if (manualPairingExpanded) {
                                    val host = state.runtime.addresses.firstOrNull() ?: return@MoneyListRow
                                    val command = "uv run money_mcp.py pair --host $host " +
                                        "--port ${state.runtime.port} --code $code"
                                    copyText(command)
                                } else {
                                    manualPairingExpanded = true
                                }
                            },
                            showChevron = !manualPairingExpanded,
                        )
                    }
                    state.runtime.pairedClientName?.let { clientName ->
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.lan_paired_client),
                            subtitle = clientName,
                            showChevron = false,
                        )
                    }
                    state.runtime.expiresAt?.let { expiresAt ->
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.lan_automatic_end),
                            trailing = DateTimeTextFormatter.format(expiresAt),
                            showChevron = false,
                        )
                    }
                }
            }
        }

        item { MoneySectionHeader(title = stringResource(R.string.lan_section_devices)) }
        item {
            MoneyListSection {
                if (state.pairedDevices.isEmpty()) {
                    MoneyListRow(
                        title = stringResource(R.string.lan_devices_empty),
                        showChevron = false,
                    )
                } else {
                    state.pairedDevices.forEachIndexed { index, device ->
                        MoneyListRow(
                            title = device.clientName,
                            subtitle = if (device.lastSeenAt > 0L) {
                                stringResource(
                                    R.string.lan_device_last_seen,
                                    DateTimeTextFormatter.format(device.lastSeenAt),
                                )
                            } else {
                                stringResource(R.string.lan_device_never_seen)
                            },
                            trailing = stringResource(R.string.lan_device_revoke),
                            onClick = { revokeTarget = device },
                            showChevron = false,
                        )
                        if (index != state.pairedDevices.lastIndex) MoneySectionDivider()
                    }
                }
            }
        }

        item {
            MoneySectionHeader(
                title = stringResource(R.string.lan_section_journal),
                trailing = stringResource(R.string.lan_journal_undo_count, state.appliedCount),
            )
        }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.lan_undo_latest),
                    subtitle = stringResource(R.string.lan_undo_description),
                    trailing = if (state.isJournalActionRunning) stringResource(R.string.lan_processing) else null,
                    onClick = if (state.appliedCount > 0 && !state.isJournalActionRunning) onUndoLatest else null,
                    showChevron = state.appliedCount > 0,
                    enabled = state.appliedCount > 0 && !state.isJournalActionRunning,
                )
                state.latestAppliedEntry?.let { entry ->
                    MoneySectionDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            stringResource(R.string.lan_current_stack_top, entry.summary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { discardTarget = entry },
                            enabled = !state.isJournalActionRunning,
                        ) {
                            Text(stringResource(R.string.lan_keep_and_discard))
                        }
                    }
                }
            }
        }

        if (state.journalEntries.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = stringResource(R.string.lan_journal_empty),
                    subtitle = stringResource(R.string.lan_journal_empty_description),
                )
            }
        } else {
            item { MoneySectionHeader(title = stringResource(R.string.lan_recent_entries)) }
            item {
                MoneyListSection {
                    state.journalEntries.forEachIndexed { index, entry ->
                        MoneyListRow(
                            title = entry.summary,
                            subtitle = stringResource(
                                R.string.lan_journal_entry_subtitle,
                                entry.clientName,
                                DateTimeTextFormatter.format(entry.createdAt),
                            ),
                            trailing = journalStatusLabel(entry.status),
                            showChevron = false,
                        )
                        if (index != state.journalEntries.lastIndex) MoneySectionDivider()
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.lan_security_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun statusLabel(status: MoneyLanServerStatus): String = when (status) {
    MoneyLanServerStatus.STOPPED -> stringResource(R.string.lan_status_stopped)
    MoneyLanServerStatus.STARTING -> stringResource(R.string.lan_status_starting)
    MoneyLanServerStatus.RUNNING -> stringResource(R.string.lan_status_running)
    MoneyLanServerStatus.ERROR -> stringResource(R.string.lan_status_error)
}

@Composable
private fun statusDescription(state: LanMcpUiState): String = when (state.runtime.status) {
    MoneyLanServerStatus.STOPPED -> stringResource(R.string.lan_status_stopped_description)
    MoneyLanServerStatus.STARTING -> stringResource(R.string.lan_status_starting_description)
    MoneyLanServerStatus.RUNNING -> if (state.runtime.pairedClientName == null) {
        stringResource(R.string.lan_status_waiting_description)
    } else {
        stringResource(R.string.lan_status_connected_description, state.runtime.pairedClientName)
    }
    MoneyLanServerStatus.ERROR -> state.runtime.errorMessage
        ?: stringResource(R.string.lan_status_error_description)
}

@Composable
private fun journalStatusLabel(status: AiMutationJournalStatus): String = when (status) {
    AiMutationJournalStatus.APPLIED -> stringResource(R.string.lan_journal_status_applied)
    AiMutationJournalStatus.UNDONE -> stringResource(R.string.lan_journal_status_undone)
    AiMutationJournalStatus.DISCARDED -> stringResource(R.string.lan_journal_status_discarded)
}
