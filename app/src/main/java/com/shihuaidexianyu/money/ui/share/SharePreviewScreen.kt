package com.shihuaidexianyu.money.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun SharePreviewScreen(
    viewModel: SharePreviewViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is SharePreviewEffect.Saved) onSaved()
    }
    if (showAccountPicker) {
        AccountPickerDialog(
            title = "选择开放账户",
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
            },
        )
    }
    dateTimeField?.let { field ->
        when (field) {
            MoneyDateTimePickerField.DATE -> MoneyDatePickerDialogHost(
                initialSelectedDateMillis = state.occurredAt,
                onDismiss = { dateTimeField = null },
                onConfirm = { selectedDate ->
                    selectedDate?.let {
                        viewModel.updateOccurredAt(
                            DateTimeTextFormatter.replaceDate(state.occurredAt, it),
                        )
                    }
                    dateTimeField = null
                },
            )
            MoneyDateTimePickerField.TIME -> MoneyTimePickerDialogHost(
                initialTimeMillis = state.occurredAt,
                onDismiss = { dateTimeField = null },
                onConfirm = { hour, minute ->
                    viewModel.updateOccurredAt(
                        DateTimeTextFormatter.replaceTime(state.occurredAt, hour, minute),
                    )
                    dateTimeField = null
                },
            )
        }
    }

    MoneyFormPage(
        title = "分享内容预览",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        item {
            MoneyCard {
                Text(if (state.isUncertain) "解析结果需要确认" else "已解析分享内容，请确认后保存")
                Text(state.originalText, maxLines = 4)
                if (state.candidateAmounts.size > 1) {
                    Text("检测到多个可能金额，请手动填写正确金额")
                }
            }
        }
        item {
            MoneyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CashFlowDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = state.direction == direction,
                            onClick = { viewModel.updateDirection(direction) },
                            label = { Text(direction.displayName) },
                        )
                    }
                }
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                )
                if (state.accounts.isEmpty() && !state.isLoading) {
                    Text("没有可记账的开放账户")
                    Button(onClick = onCreateAccount) { Text("创建账户") }
                } else {
                    MoneySelectionField(
                        label = "账户",
                        value = selectedAccount?.name ?: "请选择",
                        modifier = Modifier.clickable { showAccountPicker = true },
                    )
                }
                MoneySingleLineField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    label = "备注（可选，最多 200 字）",
                )
                Text("${state.note.length}/200")
                MoneyDateTimeFields(
                    valueMillis = state.occurredAt,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = "可修改分享记录时间",
                )
                state.fieldError?.let { Text(it) }
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    enabled = !state.isLoading && state.accounts.isNotEmpty(),
                    label = "确认并保存",
                )
            }
        }
    }
}
