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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
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

    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    MoneyFormPage(
        title = stringResource(R.string.reminder_create_title),
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
                    label = stringResource(R.string.field_name),
                    placeholder = stringResource(R.string.reminder_name_example),
                )
            }
        }
        item {
            MoneyCard {
                MoneyPickerField(
                    label = stringResource(R.string.field_type),
                    value = state.type.displayName,
                    dialogTitle = stringResource(R.string.reminder_type_dialog),
                    options = ReminderType.entries.toList(),
                    selected = state.type,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::updateType,
                )
                MoneySelectionField(
                    label = stringResource(R.string.account_single),
                    value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                    modifier = Modifier.clickable { showAccountPicker = true },
                )
                MoneyPickerField(
                    label = stringResource(R.string.field_direction),
                    value = state.direction.displayName,
                    dialogTitle = stringResource(R.string.cash_flow_direction_dialog),
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
                    label = stringResource(R.string.reminder_preset_amount),
                )
            }
        }
        item {
            MoneyCard {
                MoneyPickerField(
                    label = stringResource(R.string.reminder_period),
                    value = state.periodType.displayName,
                    dialogTitle = stringResource(R.string.reminder_period_dialog),
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
                            label = stringResource(R.string.reminder_interval_days),
                        )
                    }
                }
                MoneySingleLineField(
                    value = state.anchorDateText,
                    onValueChange = viewModel::updateAnchorDate,
                    label = stringResource(R.string.reminder_first_date),
                    isError = state.anchorError != null,
                )
                MoneySingleLineField(
                    value = state.anchorTimeText,
                    onValueChange = viewModel::updateAnchorTime,
                    label = stringResource(R.string.reminder_first_time),
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
