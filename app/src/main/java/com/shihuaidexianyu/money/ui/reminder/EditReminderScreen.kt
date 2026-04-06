package com.shihuaidexianyu.money.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyPickerField
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField

@Composable
fun EditReminderScreen(
    viewModel: EditReminderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is EditReminderEffect.Saved) onBack()
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

    if (state.isLoading) return

    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    MoneyFormPage(
        title = "编辑提醒",
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        item {
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = "名称",
                )
            }
        }
        item {
            MoneyCard {
                MoneyPickerField(
                    label = "类型",
                    value = state.type.displayName,
                    dialogTitle = "提醒类型",
                    options = ReminderType.entries.toList(),
                    selected = state.type,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::updateType,
                )
                MoneySelectionField(
                    label = "账户",
                    value = selectedAccount?.name ?: "请选择",
                    modifier = Modifier.clickable { showAccountPicker = true },
                )
                MoneyPickerField(
                    label = "方向",
                    value = state.direction.displayName,
                    dialogTitle = "收支方向",
                    options = CashFlowDirection.entries.toList(),
                    selected = state.direction,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::updateDirection,
                )
            }
        }
        item {
            MoneyCard {
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                    label = "预设金额",
                )
            }
        }
        item {
            MoneyCard {
                MoneyPickerField(
                    label = "周期",
                    value = state.periodType.displayName,
                    dialogTitle = "周期类型",
                    options = ReminderPeriodType.entries.toList(),
                    selected = state.periodType,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::updatePeriodType,
                )
                when (state.periodType) {
                    ReminderPeriodType.MONTHLY -> {
                        MoneySingleLineField(
                            value = state.periodDay,
                            onValueChange = viewModel::updatePeriodDay,
                            label = "每月几号 (1-31)",
                        )
                    }
                    ReminderPeriodType.YEARLY -> {
                        MoneySingleLineField(
                            value = state.periodMonth,
                            onValueChange = viewModel::updatePeriodMonth,
                            label = "月份 (1-12)",
                        )
                        MoneySingleLineField(
                            value = state.periodDay,
                            onValueChange = viewModel::updatePeriodDay,
                            label = "日期 (1-31)",
                        )
                    }
                    ReminderPeriodType.CUSTOM_DAYS -> {
                        MoneySingleLineField(
                            value = state.periodCustomDays,
                            onValueChange = viewModel::updatePeriodCustomDays,
                            label = "间隔天数",
                        )
                    }
                }
            }
        }
        item {
            MoneyCard {
                MoneySelectionField(
                    label = "启用",
                    value = if (state.isEnabled) "是" else "否",
                )
                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = viewModel::updateEnabled,
                )
            }
        }
        item {
            MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving)
        }
    }
}
