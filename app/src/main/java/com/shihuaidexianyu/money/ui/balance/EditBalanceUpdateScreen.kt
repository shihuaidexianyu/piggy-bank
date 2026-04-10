package com.shihuaidexianyu.money.ui.balance

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun EditBalanceUpdateScreen(
    viewModel: EditBalanceUpdateViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        when (effect) {
            EditBalanceUpdateEffect.Saved -> onBack()
            EditBalanceUpdateEffect.Deleted -> onDeleted()
            else -> {}
        }
    }

    if (state.showDeleteConfirm) {
        MoneyConfirmDialog(
            title = "撤销余额更新",
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
        title = "修改余额更新",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
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
                        value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyAmountField(
                        value = state.actualBalanceText,
                        onValueChange = viewModel::updateActualBalance,
                        label = "实际余额",
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
                )
                MoneyInlineLabelValue(
                    label = "实际余额",
                    value = state.actualBalancePreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                )
                MoneyInlineLabelValue(
                    label = "差额",
                    value = state.deltaPreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                )
                MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, enabled = !state.isLoading, label = "保存修改")
            }
        }
        item {
            MoneyCard {
                OutlinedButton(
                    onClick = viewModel::showDeleteConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isSaving,
                ) {
                    Text("撤销这次更新")
                }
            }
        }
    }
}
