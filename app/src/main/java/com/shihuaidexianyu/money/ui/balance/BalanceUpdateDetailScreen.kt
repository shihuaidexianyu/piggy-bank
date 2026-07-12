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
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
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
    val deletedMessage = stringResource(R.string.ledger_record_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            terminal.ledgerUndoToken?.let {
                dispatcher?.dispatch(rootSnackbarEffect(deletedMessage, undoLabel, RootSnackbarAction.RestoreLedger(it), terminal.token))
            }
            if (terminal.kind == FormTerminalKind.DELETED) onDeleted()
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (showDeleteConfirm) {
        val isReconcile = state.delta == 0L
        MoneyConfirmDialog(
            title = stringResource(
                if (isReconcile) R.string.balance_undo_reconciliation else R.string.balance_undo_adjustment,
            ),
            message = stringResource(R.string.balance_undo_message),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = stringResource(R.string.balance_confirm_undo),
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    MoneyFormPage(
        title = stringResource(balanceUpdateDetailTitleRes(state)),
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
                    Text(stringResource(R.string.loading_ellipsis), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = stringResource(R.string.field_occurred_time),
                        value = DateTimeTextFormatter.format(state.occurredAt),
                    )
                    MoneyInlineLabelValue(
                        label = stringResource(R.string.balance_before_reconciliation),
                        value = formatInAppAmount(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyInlineLabelValue(
                        label = stringResource(R.string.balance_confirmed),
                        value = formatInAppAmount(state.actualBalance, settings),
                    )
                    MoneyInlineLabelValue(
                        label = stringResource(R.string.balance_delta),
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
                    Text(stringResource(R.string.action_edit_record))
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isDeleting,
                ) {
                    Text(
                        stringResource(
                            if (state.isDeleting) R.string.action_deleting else R.string.action_delete_this_record,
                        ),
                    )
                }
            }
        }
    }
}

internal fun balanceUpdateDetailTitleRes(state: BalanceUpdateDetailUiState): Int =
    if (state.isLoading || state.loadErrorMessage != null) {
        R.string.balance_detail_neutral_title
    } else if (state.delta == 0L) {
        R.string.balance_detail_reconciliation_title
    } else {
        R.string.balance_detail_adjustment_title
    }
