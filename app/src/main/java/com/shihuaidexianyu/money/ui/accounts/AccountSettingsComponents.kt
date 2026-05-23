package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost

internal enum class AccountSettingsPicker {
    REMINDER_WEEKDAY,
    REMINDER_TIME,
}

@Composable
internal fun AccountSettingsPickerDialog(
    picker: AccountSettingsPicker?,
    reminderConfig: BalanceUpdateReminderConfig,
    onDismiss: () -> Unit,
    onReminderWeekdaySelected: (BalanceUpdateReminderWeekday) -> Unit,
    onReminderTimeSelected: (Int, Int) -> Unit,
) {
    when (picker) {
        AccountSettingsPicker.REMINDER_WEEKDAY -> {
            MoneyChoiceDialog(
                title = "每周提醒日",
                options = BalanceUpdateReminderWeekday.entries,
                selected = reminderConfig.weekday,
                label = { it.displayName },
                onSelect = {
                    onReminderWeekdaySelected(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.REMINDER_TIME -> {
            val initialTimeMillis = java.time.LocalDate.now()
                .atTime(reminderConfig.hour, reminderConfig.minute)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            MoneyTimePickerDialogHost(
                initialTimeMillis = initialTimeMillis,
                onDismiss = onDismiss,
                onConfirm = { hour, minute ->
                    onReminderTimeSelected(hour, minute)
                    onDismiss()
                },
            )
        }

        null -> Unit
    }
}

@Composable
internal fun AccountReminderFields(
    reminderConfig: BalanceUpdateReminderConfig,
    onReminderWeekdayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneySelectionField(
        label = "每周提醒日",
        value = reminderConfig.weekday.displayName,
        subtitle = "到了提醒时间后未核对会标记为待核对",
        modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
    )
    MoneySelectionField(
        label = "提醒时间",
        value = reminderConfig.timeText,
        modifier = Modifier.clickable(onClick = onReminderTimeClick),
    )
}

@Composable
internal fun AccountReminderListSection(
    reminderConfig: BalanceUpdateReminderConfig,
    onReminderWeekdayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneyListSection {
        MoneyListRow(
            title = "每周提醒日",
            subtitle = "到了提醒时间后未核对会标记为待核对",
            trailing = reminderConfig.weekday.displayName,
            modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "提醒时间",
            trailing = reminderConfig.timeText,
            modifier = Modifier.clickable(onClick = onReminderTimeClick),
        )
    }
}
