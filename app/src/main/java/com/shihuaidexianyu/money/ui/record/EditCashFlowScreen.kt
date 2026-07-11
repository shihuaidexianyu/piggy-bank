package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
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
import com.shihuaidexianyu.money.ui.common.formAsyncContent
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

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            when (terminal.kind) {
                FormTerminalKind.SAVED -> onBack()
                FormTerminalKind.DELETED -> {
                    terminal.ledgerUndoToken?.let { undoToken ->
                        rootSnackbarDispatcher?.dispatch(
                            rootSnackbarEffect(
                                message = "记录已删除",
                                actionLabel = "撤销",
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
            title = "删除记录",
            message = "删除后将重新计算相关账户余额，确认删除？",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = "确认删除",
            dismissLabel = "取消",
        )
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = "选择账户",
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
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
        title = editCashFlowTitle(state),
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
                    text = "此修改会影响当前余额",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                    isError = state.amountError != null,
                    supportingText = state.amountError,
                )
                MoneySelectionField(
                    label = "账户",
                    value = selectedAccount?.name ?: "请选择",
                    modifier = Modifier.clickable { showAccountPicker = true },
                    isError = state.accountError != null,
                    supportingText = state.accountError,
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

internal fun editCashFlowTitle(state: EditCashFlowUiState): String =
    if (state.isLoading || state.loadErrorMessage != null) {
        "编辑收支记录"
    } else {
        "编辑${state.direction.displayName}"
    }
