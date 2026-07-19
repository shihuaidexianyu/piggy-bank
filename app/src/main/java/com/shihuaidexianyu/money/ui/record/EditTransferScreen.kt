package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors

private enum class EditTransferPickerTarget {
    FROM,
    TO,
}

@Composable
fun EditTransferScreen(
    viewModel: EditTransferViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickerTarget by remember { mutableStateOf<EditTransferPickerTarget?>(null) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val fromAccount = state.accounts.firstOrNull { it.id == state.fromAccountId }
    val toAccount = state.accounts.firstOrNull { it.id == state.toAccountId }
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
                                deletedMessage,
                                undoLabel,
                                RootSnackbarAction.RestoreLedger(undoToken),
                                terminal.token,
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

    pickerTarget?.let { target ->
        AccountPickerDialog(
            title = stringResource(
                if (target == EditTransferPickerTarget.FROM) R.string.transfer_choose_from else R.string.transfer_choose_to,
            ),
            accounts = state.accounts,
            selectedAccountId = if (target == EditTransferPickerTarget.FROM) state.fromAccountId else state.toAccountId,
            disabledAccountIds = setOfNotNull(
                if (target == EditTransferPickerTarget.FROM) state.toAccountId else state.fromAccountId,
            ),
            onDismiss = { pickerTarget = null },
            onPick = {
                if (target == EditTransferPickerTarget.FROM) {
                    viewModel.updateFromAccount(it)
                } else {
                    viewModel.updateToAccount(it)
                }
                pickerTarget = null
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
        title = stringResource(R.string.transfer_edit_title),
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
            MoneyCard {
                Text(
                    text = stringResource(R.string.transfer_edit_balance_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyAmountHeroField(
                    value = state.amountText,
                    label = stringResource(R.string.field_amount),
                    accent = moneyColors.transfer,
                    onValueChange = viewModel::updateAmount,
                    isError = state.amountError != null,
                    supportingText = state.amountError,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
                if (state.allFromAccountAmount > 0L) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SuggestionChip(
                                onClick = viewModel::useAllFromAccountBalance,
                                label = { Text(stringResource(R.string.transfer_all)) },
                            )
                        }
                    }
                }
                MoneySelectionField(
                    label = stringResource(R.string.transfer_from_account),
                    value = fromAccount?.name ?: stringResource(R.string.field_please_choose),
                    modifier = Modifier.clickable { pickerTarget = EditTransferPickerTarget.FROM },
                    isError = state.fromAccountError != null,
                    supportingText = state.fromAccountError,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::swapAccounts) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Text(stringResource(R.string.transfer_swap_accounts), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                MoneySelectionField(
                    label = stringResource(R.string.transfer_to_account),
                    value = toAccount?.name ?: stringResource(R.string.field_please_choose),
                    modifier = Modifier.clickable { pickerTarget = EditTransferPickerTarget.TO },
                    isError = state.toAccountError != null,
                    supportingText = state.toAccountError,
                )
            }
        }
        item {
            MoneyCard {
                MoneySingleLineField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    label = stringResource(R.string.field_optional_note),
                    isError = state.noteError != null,
                    supportingText = state.noteError,
                )
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = stringResource(R.string.ledger_edit_time_description),
                    errorText = state.occurredAtError,
                )
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    enabled = !state.isLoading && !state.hasConflict && state.pendingTerminal == null,
                    label = stringResource(R.string.action_save_changes),
                )
                if (state.hasConflict) {
                    OutlinedButton(
                        onClick = viewModel::reload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ledger_reload_latest))
                    }
                }
            }
        }
        item {
            MoneyCard {
                OutlinedButton(
                    onClick = viewModel::showDeleteConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isSaving && state.pendingTerminal == null,
                ) {
                    Text(stringResource(R.string.ledger_delete_title))
                }
            }
        }
    }
}
