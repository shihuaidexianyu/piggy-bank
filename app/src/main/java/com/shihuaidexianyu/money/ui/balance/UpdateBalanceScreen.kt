package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlin.math.abs

@Composable
fun UpdateBalanceScreen(
    viewModel: UpdateBalanceViewModel,
    settings: AppSettings,
    onShowResult: () -> Unit,
    onStartCashFlow: (CashFlowDirection, Long, Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is UpdateBalanceEffect.Saved) onShowResult()
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
        title = "核对余额",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        item {
            MoneyCard {
                MoneySelectionField(
                    label = "账户",
                    value = selectedAccount?.name ?: "请选择",
                    modifier = Modifier.clickable { showAccountPicker = true },
                )
                MoneyInlineLabelValue(
                    label = "系统余额",
                    value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                )
                MoneyAmountField(
                    value = state.actualBalanceText,
                    onValueChange = viewModel::updateActualBalance,
                    label = "实际余额",
                )
                if (state.actualBalanceEdited || state.deltaPreview != 0L || state.actualBalancePreview == null) {
                    OutlinedButton(
                        onClick = viewModel::resetActualBalanceToSystem,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("设为无变化")
                    }
                }
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = "默认当前时间",
                )
            }
        }
        item {
            MoneyCard {
                MoneyInlineLabelValue(
                    label = "系统余额",
                    value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                )
                MoneyInlineLabelValue(
                    label = "实际余额",
                    value = state.actualBalancePreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                )
                MoneyInlineLabelValue(
                    label = "差额",
                    value = state.deltaPreview?.let { AmountFormatter.format(it, settings) } ?: "-",
                )
                state.deltaPreview?.let {
                    Text(
                        text = when {
                            it > 0 -> "高于系统记录"
                            it < 0 -> "低于系统记录"
                            else -> "余额无变化，可直接确认"
                        },
                        color = when {
                            it > 0 -> LocalMoneyColors.current.income
                            it < 0 -> LocalMoneyColors.current.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val nonZeroDelta = state.deltaPreview
                if (nonZeroDelta != null && nonZeroDelta != 0L) {
                    Text(
                        text = "如果这是漏记的收支，可以先补记一笔；如果只是实际余额修正，可直接保存本次核对。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val accountId = state.selectedAccountId
                    if (accountId != null) {
                        val prefillAmount = abs(nonZeroDelta)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onStartCashFlow(
                                        CashFlowDirection.INFLOW,
                                        accountId,
                                        prefillAmount,
                                    )
                                },
                                enabled = !state.isSaving,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("补记收入")
                            }
                            OutlinedButton(
                                onClick = {
                                    onStartCashFlow(
                                        CashFlowDirection.OUTFLOW,
                                        accountId,
                                        prefillAmount,
                                    )
                                },
                                enabled = !state.isSaving,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("补记支出")
                            }
                        }
                    }
                }
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    label = if (state.deltaPreview == 0L) "确认无变化" else "保存余额核对",
                )
            }
        }
    }
}
