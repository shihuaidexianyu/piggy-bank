package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
fun BalanceAdjustmentDetailScreen(
    viewModel: BalanceAdjustmentDetailViewModel,
    state: BalanceAdjustmentDetailUiState,
    settings: PortableSettings,
    onClosed: () -> Unit,
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
            onClosed()
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (showDeleteConfirm) {
        MoneyConfirmDialog(
            title = "删除余额矫正",
            message = "删除后会重新计算该账户当前余额，确认继续？",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = "确认删除",
            dismissLabel = "取消",
        )
    }

    MoneyFormPage(
        title = "余额矫正详情",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "adjustment-detail"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item {
            MoneyCard {
                if (!state.isLoading) {
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = "时间",
                        value = DateTimeTextFormatter.format(state.occurredAt),
                    )
                    MoneyInlineLabelValue(
                        label = "矫正差额",
                        value = formatInAppAmount(state.delta, settings),
                    )
                }
            }
        }
        if (!state.isLoading) {
            item {
                MoneyCard {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isDeleting,
                    ) {
                        Text(if (state.isDeleting) "删除中..." else "删除这次记录")
                    }
                }
            }
        }
    }
}

