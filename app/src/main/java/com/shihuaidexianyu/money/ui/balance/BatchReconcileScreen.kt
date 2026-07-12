package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BatchReconcileScreen(
    viewModel: BatchReconcileViewModel,
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            if (terminal.kind == FormTerminalKind.SAVED) onSaved(requireNotNull(terminal.count))
            viewModel.ackTerminal(terminal.token)
        }
    }

    MoneyFormPage(
        title = stringResource(R.string.batch_reconcile_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "batch-reconcile"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }

        if (state.accounts.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = stringResource(R.string.batch_reconcile_empty),
                    subtitle = stringResource(R.string.batch_reconcile_empty_description),
                    action = {
                        Button(
                            onClick = guardedBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_back_home))
                        }
                    },
                )
            }
            return@MoneyFormPage
        }

        item {
            MoneyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoneyStatusPill(
                        text = stringResource(R.string.batch_reconcile_pending_format, state.accounts.size),
                    )
                    Text(
                        text = stringResource(R.string.batch_reconcile_selected_format, state.selectedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MoneyCard(contentPadding = PaddingValues(0.dp)) {
                Column {
                    state.accounts.forEachIndexed { index, account ->
                        BatchReconcileAccountRow(
                            account = account,
                            state = state,
                            onToggle = { viewModel.toggleAccount(account.accountId) },
                        )
                        if (index != state.accounts.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
        }

        item {
            MoneyCard {
                MoneySaveButton(
                    onClick = viewModel::saveSelected,
                    isSaving = state.isSaving,
                    enabled = state.selectedCount > 0 && state.pendingTerminal == null,
                    label = stringResource(R.string.balance_confirm_unchanged),
                )
            }
        }
    }
}

@Composable
private fun BatchReconcileAccountRow(
    account: BatchReconcileAccountUiModel,
    state: BatchReconcileUiState,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isSaving, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = account.isSelected,
            onCheckedChange = { onToggle() },
            enabled = !state.isSaving,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = account.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MoneyStatusPill(
                    text = stringResource(
                        if (account.isFailed) R.string.status_failed else R.string.account_stale_badge,
                    ),
                    accent = if (account.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                text = account.lastBalanceUpdateAt?.let {
                    stringResource(R.string.batch_reconcile_last_format, DateTimeTextFormatter.format(it))
                } ?: stringResource(R.string.batch_reconcile_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatInAppAmount(account.systemBalance, state.settings),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}
