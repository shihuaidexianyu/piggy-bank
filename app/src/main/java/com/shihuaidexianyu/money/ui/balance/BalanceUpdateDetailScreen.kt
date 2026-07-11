package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BalanceUpdateDetailScreen(
    viewModel: BalanceUpdateDetailViewModel,
    state: BalanceUpdateDetailUiState,
    settings: PortableSettings,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dispatcher = LocalRootSnackbarDispatcher.current

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            terminal.ledgerUndoToken?.let {
                dispatcher?.dispatch(rootSnackbarEffect("记录已删除", "撤销", RootSnackbarAction.RestoreLedger(it), terminal.token))
            }
            if (terminal.kind == FormTerminalKind.DELETED) onDeleted()
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (showDeleteConfirm) {
        val isReconcile = state.delta == 0L
        MoneyConfirmDialog(
            title = if (isReconcile) "撤销余额核对" else "撤销对账调整",
            message = "撤销后会重新计算该账户当前余额，确认继续？",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = "确认撤销",
            dismissLabel = "取消",
        )
    }

    MoneyFormPage(
        title = balanceUpdateDetailTitle(state),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "balance-detail"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item {
            MoneyCard {
                if (state.isLoading) {
                    Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = "时间",
                        value = DateTimeTextFormatter.format(state.occurredAt),
                    )
                    MoneyInlineLabelValue(
                        label = "对账前账面余额",
                        value = formatInAppAmount(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "本次确认余额",
                        value = formatInAppAmount(state.actualBalance, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "差额",
                        value = formatInAppAmount(state.delta, settings),
                    )
                }
            }
        }
        item {
            MoneyCard {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isDeleting,
                ) {
                    Text("修改记录")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isDeleting,
                ) {
                    Text(if (state.isDeleting) "删除中..." else "删除这次记录")
                }
            }
        }
    }
}

internal fun balanceUpdateDetailTitle(state: BalanceUpdateDetailUiState): String =
    if (state.isLoading || state.loadErrorMessage != null) {
        "对账记录详情"
    } else if (state.delta == 0L) {
        "余额核对详情"
    } else {
        "对账调整详情"
    }
