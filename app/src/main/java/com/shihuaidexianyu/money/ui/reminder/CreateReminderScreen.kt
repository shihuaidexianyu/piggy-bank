package com.shihuaidexianyu.money.ui.reminder

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
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import java.time.ZoneId

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
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)

    MoneyDateTimePickerHost(
        field = dateTimeField,
        currentMillis = reminderAnchorToMillis(
            state.anchorDateText,
            state.anchorTimeText,
            ZoneId.systemDefault(),
        ) ?: System.currentTimeMillis(),
        onPick = { millis ->
            val (date, time) = formatReminderAnchor(millis, ZoneId.systemDefault())
            viewModel.updateAnchorDate(date)
            viewModel.updateAnchorTime(time)
        },
        onDismiss = { dateTimeField = null },
    )

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
        onBack = guardedBack,
        modifier = modifier,
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "create-reminder"),
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
                    onClick = { showAccountPicker = true },
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
                MoneySelectionField(
                    label = stringResource(R.string.reminder_first_date),
                    value = state.anchorDateText,
                    onClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    isError = state.anchorError != null,
                )
                MoneySelectionField(
                    label = stringResource(R.string.reminder_first_time),
                    value = state.anchorTimeText,
                    onClick = { dateTimeField = MoneyDateTimePickerField.TIME },
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
