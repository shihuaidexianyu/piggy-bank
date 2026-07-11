package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun EditBalanceUpdateScreen(
    viewModel: EditBalanceUpdateViewModel,
    settings: PortableSettings,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
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
            title = "撤销对账记录",
            message = "撤销后会重新计算该账户当前余额，确认继续？",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = "确认撤销",
            dismissLabel = "取消",
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
        title = "修改对账记录",
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
                if (state.isLoading) {
                    Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = "这次修改会影响当前余额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyInlineLabelValue(label = "账户", value = state.accountName)
                    MoneyInlineLabelValue(
                        label = "系统余额",
                        value = formatInAppAmount(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyAmountField(
                        value = state.actualBalanceText,
                        onValueChange = viewModel::updateActualBalance,
                        label = "实际余额",
                        allowSigned = true,
                        isError = state.actualBalanceError != null,
                        supportingText = state.actualBalanceError,
                    )
                }
            }
        }
        item {
            MoneyCard {
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = "修改本次更新的发生时间",
                    errorText = state.occurredAtError,
                )
                MoneyInlineLabelValue(
                    label = "实际余额",
                    value = state.actualBalancePreview?.let { formatInAppAmount(it, settings) } ?: "-",
                )
                MoneyInlineLabelValue(
                    label = "差额",
                    value = state.deltaPreview?.let { formatInAppAmount(it, settings) } ?: "-",
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
                    Text("撤销这次更新")
                }
            }
        }
    }
}
