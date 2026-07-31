package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInsetDivider
import com.shihuaidexianyu.money.ui.common.MoneyInsetGroup
import com.shihuaidexianyu.money.ui.common.MoneyInsetRow
import com.shihuaidexianyu.money.ui.common.MoneyInsetTextRow
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun EditCashFlowScreen(
    viewModel: EditCashFlowViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current
    val deletedMessage = stringResource(R.string.ledger_record_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val moneyColors = LocalMoneyColors.current

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            when (terminal.kind) {
                FormTerminalKind.SAVED -> onBack()
                FormTerminalKind.DELETED -> {
                    terminal.ledgerUndoToken?.let { undoToken ->
                        rootSnackbarDispatcher?.dispatch(
                            rootSnackbarEffect(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                action = RootSnackbarAction.RestoreLedger(undoToken),
                                token = terminal.token,
                            ),
                        )
                    }
                    onDeleted()
                }
            }
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (state.showDeleteConfirm) {
        MoneyConfirmDialog(
            title = stringResource(R.string.ledger_delete_title),
            message = stringResource(R.string.ledger_delete_balance_warning),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = stringResource(R.string.ledger_confirm_delete),
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = stringResource(R.string.account_choose),
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
            },
        )
    }

    MoneyDateTimePickerHost(
        field = dateTimeField,
        currentMillis = state.occurredAtMillis,
        onPick = viewModel::updateOccurredAt,
        onDismiss = { dateTimeField = null },
    )

    MoneyFormPage(
        title = when (val titleRes = editCashFlowTitleRes(state)) {
            R.string.cash_flow_edit_title -> stringResource(titleRes)
            else -> stringResource(titleRes, state.direction.displayName)
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, state.loadRetryToken),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item {
            Text(
                text = stringResource(R.string.cash_flow_edit_balance_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MoneyAmountHeroField(
                value = state.amountText,
                label = stringResource(R.string.field_amount),
                accent = if (state.direction == CashFlowDirection.INFLOW) {
                    moneyColors.income
                } else {
                    moneyColors.expense
                },
                onValueChange = viewModel::updateAmount,
                isError = state.amountError != null,
                supportingText = state.amountError,
            )
        }
        item {
            MoneyInsetGroup {
                MoneyInsetRow(
                    label = stringResource(R.string.account_single),
                    value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                    onClick = { showAccountPicker = true },
                    showChevron = true,
                    isError = state.accountError != null,
                    supportingText = state.accountError,
                )
                MoneyInsetDivider()
                MoneyInsetTextRow(
                    label = stringResource(R.string.field_optional_note),
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    isError = state.noteError != null,
                    supportingText = state.noteError,
                )
                MoneyInsetDivider()
                MoneyInsetRow(
                    label = stringResource(R.string.field_date),
                    value = DateTimeTextFormatter.formatDateOnly(state.occurredAtMillis),
                    onClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    isError = state.occurredAtError != null,
                )
                MoneyInsetDivider()
                MoneyInsetRow(
                    label = stringResource(R.string.field_time),
                    value = DateTimeTextFormatter.formatTimeOnly(state.occurredAtMillis),
                    onClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    isError = state.occurredAtError != null,
                    supportingText = state.occurredAtError,
                )
            }
        }
        item {
            MoneySaveButton(
                onClick = viewModel::save,
                isSaving = state.isSaving,
                enabled = !state.isLoading && !state.hasConflict && state.pendingTerminal == null,
                label = stringResource(R.string.action_save_changes),
            )
        }
        if (state.hasConflict) {
            item {
                MoneyTonalButton(
                    onClick = viewModel::reload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ledger_reload_latest))
                }
            }
        }
        item {
            MoneyInsetGroup {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            enabled = !state.isLoading && !state.isSaving && state.pendingTerminal == null,
                            role = Role.Button,
                            onClick = viewModel::showDeleteConfirm,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ledger_delete_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

internal fun editCashFlowTitleRes(state: EditCashFlowUiState): Int =
    if (state.isLoading || state.loadErrorMessage != null) {
        R.string.cash_flow_edit_title
    } else {
        R.string.cash_flow_edit_direction_format
    }
