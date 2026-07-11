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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

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

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            when (terminal.kind) {
                FormTerminalKind.SAVED -> onBack()
                FormTerminalKind.DELETED -> {
                    terminal.ledgerUndoToken?.let { undoToken ->
                        rootSnackbarDispatcher?.dispatch(
                            rootSnackbarEffect(
                                "记录已删除",
                                "撤销",
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
            title = "删除记录",
            message = "删除后将重新计算相关账户余额，确认删除？",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = "确认删除",
            dismissLabel = "取消",
        )
    }

    pickerTarget?.let { target ->
        AccountPickerDialog(
            title = if (target == EditTransferPickerTarget.FROM) "选择转出账户" else "选择转入账户",
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

    dateTimeField?.let { currentField ->
        when (currentField) {
            MoneyDateTimePickerField.DATE -> {
                MoneyDatePickerDialogHost(
                    initialSelectedDateMillis = state.occurredAtMillis,
                    onDismiss = { dateTimeField = null },
                    onConfirm = { selectedDate ->
                        selectedDate?.let {
                            viewModel.updateOccurredAt(
                                DateTimeTextFormatter.replaceDate(
                                    baseTimeMillis = state.occurredAtMillis,
                                    selectedDateMillis = it,
                                ),
                            )
                        }
                        dateTimeField = null
                    },
                )
            }

            MoneyDateTimePickerField.TIME -> {
                MoneyTimePickerDialogHost(
                    initialTimeMillis = state.occurredAtMillis,
                    onDismiss = { dateTimeField = null },
                    onConfirm = { hour, minute ->
                        viewModel.updateOccurredAt(
                            DateTimeTextFormatter.replaceTime(
                                baseTimeMillis = state.occurredAtMillis,
                                hour = hour,
                                minute = minute,
                            ),
                        )
                        dateTimeField = null
                    },
                )
            }
        }
    }

    MoneyFormPage(
        title = "编辑转账",
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
                    text = "此修改会影响相关账户余额",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                    isError = state.amountError != null,
                    supportingText = state.amountError,
                )
                if (state.allFromAccountAmount > 0L) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            SuggestionChip(
                                onClick = viewModel::useAllFromAccountBalance,
                                label = { Text("全部转出") },
                            )
                        }
                    }
                }
                MoneySelectionField(
                    label = "转出账户",
                    value = fromAccount?.name ?: "请选择",
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
                        Text("互换账户", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                MoneySelectionField(
                    label = "转入账户",
                    value = toAccount?.name ?: "请选择",
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
                    label = "备注（可选）",
                    isError = state.noteError != null,
                    supportingText = state.noteError,
                )
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = "修改记录发生时间",
                    errorText = state.occurredAtError,
                )
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    enabled = !state.isLoading && !state.hasConflict && state.pendingTerminal == null,
                    label = "保存修改",
                )
                if (state.hasConflict) {
                    OutlinedButton(
                        onClick = viewModel::reload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重新加载最新记录")
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
                    Text("删除记录")
                }
            }
        }
    }
}
