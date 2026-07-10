package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf<AccountSettingsPicker?>(null) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is CreateAccountEffect.Saved) onBack()
    }

    AccountSettingsPickerDialog(
        picker = picker,
        colorName = state.colorName,
        iconName = state.iconName,
        reminderConfig = state.reminderConfig,
        onDismiss = { picker = null },
        onColorSelected = viewModel::updateColorName,
        onIconSelected = viewModel::updateIconName,
        onReminderPeriodSelected = viewModel::updateReminderPeriod,
        onReminderWeekdaySelected = viewModel::updateReminderWeekday,
        onReminderMonthDaySelected = viewModel::updateReminderMonthDay,
        onReminderTimeSelected = viewModel::updateReminderTime,
    )

    MoneyFormPage(
        title = "新建账户",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        item {
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = "账户名称",
                )
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmountText,
                    label = "当前余额",
                    allowSigned = true,
                )
                AccountVisualFields(
                    colorName = state.colorName,
                    iconName = state.iconName,
                    onColorClick = { picker = AccountSettingsPicker.COLOR },
                    onIconClick = { picker = AccountSettingsPicker.ICON },
                )
                AccountReminderFields(
                    reminderConfig = state.reminderConfig,
                    onReminderPeriodClick = { picker = AccountSettingsPicker.REMINDER_PERIOD },
                    onReminderWeekdayClick = { picker = AccountSettingsPicker.REMINDER_WEEKDAY },
                    onReminderMonthDayClick = { picker = AccountSettingsPicker.REMINDER_MONTH_DAY },
                    onReminderTimeClick = { picker = AccountSettingsPicker.REMINDER_TIME },
                )
                MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving)
            }
        }
    }
}
