package com.shihuaidexianyu.money.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyPickerField
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField

@Composable
fun CreateReminderScreen(
    viewModel: CreateReminderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is CreateReminderEffect.Saved) onSaved()
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

    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    MoneyFormPage(
        title = "新建提醒",
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        modifier = modifier,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "create-reminder"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item {
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = "名称",
                    placeholder = "如：话费、Netflix",
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
                    ReminderPeriodType.MONTHLY,
                    ReminderPeriodType.YEARLY,
                    -> Unit
                    ReminderPeriodType.CUSTOM_DAYS -> {
                        MoneySingleLineField(
                            value = state.periodCustomDays,
                            onValueChange = viewModel::updatePeriodCustomDays,
                            label = "间隔天数",
                        )
                    }
                }
                MoneySingleLineField(
                    value = state.anchorDateText,
                    onValueChange = viewModel::updateAnchorDate,
                    label = "首次日期 (YYYY-MM-DD)",
                    isError = state.anchorError != null,
                )
                MoneySingleLineField(
                    value = state.anchorTimeText,
                    onValueChange = viewModel::updateAnchorTime,
                    label = "首次时间 (HH:mm)",
                    isError = state.anchorError != null,
                    supportingText = state.anchorError,
                )
            }
        }
        item {
            MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving)
        }
    }
}
