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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        title = "账户核对",
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
                    title = "暂无待核对账户",
                    subtitle = "所有账户余额都已在提醒周期内确认。",
                    action = {
                        Button(
                            onClick = guardedBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("返回首页")
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
                    MoneyStatusPill(text = "待核对 ${state.accounts.size} 个")
                    Text(
                        text = "已选择 ${state.selectedCount} 个",
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
                    label = "确认无变化",
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
                    text = if (account.isFailed) "失败" else "待核对",
                    accent = if (account.isFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                text = account.lastBalanceUpdateAt?.let {
                    "最近核对 ${DateTimeTextFormatter.format(it)}"
                } ?: "尚未核对",
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
