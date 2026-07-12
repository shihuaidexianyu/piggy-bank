package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.ui.common.AccountColorOptions
import com.shihuaidexianyu.money.ui.common.AccountColorSwatch
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.AccountIconOptions
import com.shihuaidexianyu.money.ui.common.accountIconLabel
import com.shihuaidexianyu.money.ui.common.accountColorLabel
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneyTimePickerDialogHost

internal enum class AccountSettingsPicker {
    COLOR,
    ICON,
    REMINDER_PERIOD,
    REMINDER_WEEKDAY,
    REMINDER_MONTH_DAY,
    REMINDER_TIME,
}

@Composable
internal fun AccountSettingsPickerDialog(
    picker: AccountSettingsPicker?,
    colorName: String,
    iconName: String,
    reminderConfig: BalanceUpdateReminderConfig,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onIconSelected: (String) -> Unit,
    onReminderPeriodSelected: (BalanceUpdateReminderPeriod) -> Unit,
    onReminderWeekdaySelected: (BalanceUpdateReminderWeekday) -> Unit,
    onReminderMonthDaySelected: (Int) -> Unit,
    onReminderTimeSelected: (Int, Int) -> Unit,
) {
    when (picker) {
        AccountSettingsPicker.COLOR -> {
            MoneyChoiceDialog(
                title = stringResource(R.string.account_color_title),
                options = AccountColorOptions,
                selected = AccountColorOptions.firstOrNull { it.name == normalizeAccountColorName(colorName) },
                label = { stringResource(it.labelRes) },
                onSelect = {
                    onColorSelected(it.name)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.ICON -> {
            AccountIconChoiceDialog(
                selectedIconName = iconName,
                onSelect = {
                    onIconSelected(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.REMINDER_PERIOD -> {
            MoneyChoiceDialog(
                title = stringResource(R.string.account_reminder_period),
                options = BalanceUpdateReminderPeriod.entries,
                selected = reminderConfig.period,
                label = { it.displayName },
                onSelect = {
                    onReminderPeriodSelected(it)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        AccountSettingsPicker.REMINDER_WEEKDAY -> {
            MoneyChoiceDialog(
                title = stringResource(R.string.account_reminder_weekday_dialog),
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

        AccountSettingsPicker.REMINDER_MONTH_DAY -> {
            MoneyChoiceDialog(
                title = stringResource(R.string.account_reminder_month_day_dialog),
                options = (1..31).toList(),
                selected = reminderConfig.monthDay,
                label = { stringResource(R.string.account_month_day_format, it) },
                onSelect = {
                    onReminderMonthDaySelected(it)
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
private fun AccountIconChoiceDialog(
    selectedIconName: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_icon_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountIconOptions.forEach { option ->
                    val selected = option.name == normalizeAccountIconName(selectedIconName)
                    TextButton(
                        onClick = { onSelect(option.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = stringResource(option.labelRes),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
internal fun AccountReminderFields(
    reminderConfig: BalanceUpdateReminderConfig,
    onReminderPeriodClick: () -> Unit,
    onReminderWeekdayClick: () -> Unit,
    onReminderMonthDayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneySelectionField(
        label = stringResource(R.string.account_reminder_period),
        value = reminderConfig.period.displayName,
        subtitle = stringResource(R.string.account_reminder_description),
        modifier = Modifier.clickable(onClick = onReminderPeriodClick),
    )
    when (reminderConfig.period) {
        BalanceUpdateReminderPeriod.WEEKLY -> MoneySelectionField(
            label = stringResource(R.string.account_reminder_weekday),
            value = reminderConfig.weekday.displayName,
            modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
        )

        BalanceUpdateReminderPeriod.MONTHLY -> MoneySelectionField(
            label = stringResource(R.string.account_reminder_month_day),
            value = stringResource(R.string.account_month_day_format, reminderConfig.monthDay),
            modifier = Modifier.clickable(onClick = onReminderMonthDayClick),
        )
    }
    MoneySelectionField(
        label = stringResource(R.string.account_reminder_time),
        value = reminderConfig.timeText,
        modifier = Modifier.clickable(onClick = onReminderTimeClick),
    )
}

@Composable
internal fun AccountVisualFields(
    colorName: String,
    iconName: String,
    onColorClick: () -> Unit,
    onIconClick: () -> Unit,
) {
    MoneySelectionField(
        label = stringResource(R.string.account_icon_title),
        value = accountIconLabel(iconName),
        modifier = Modifier.clickable(onClick = onIconClick),
    )
    MoneySelectionField(
        label = stringResource(R.string.account_color_title),
        value = accountColorLabel(colorName),
        modifier = Modifier.clickable(onClick = onColorClick),
    )
}

@Composable
internal fun AccountReminderListSection(
    reminderConfig: BalanceUpdateReminderConfig,
    onReminderPeriodClick: () -> Unit,
    onReminderWeekdayClick: () -> Unit,
    onReminderMonthDayClick: () -> Unit,
    onReminderTimeClick: () -> Unit,
) {
    MoneyListSection {
        MoneyListRow(
            title = stringResource(R.string.account_reminder_period),
            subtitle = stringResource(R.string.account_reminder_description),
            trailing = reminderConfig.period.displayName,
            modifier = Modifier.clickable(onClick = onReminderPeriodClick),
        )
        MoneySectionDivider()
        when (reminderConfig.period) {
            BalanceUpdateReminderPeriod.WEEKLY -> MoneyListRow(
                title = stringResource(R.string.account_reminder_weekday),
                trailing = reminderConfig.weekday.displayName,
                modifier = Modifier.clickable(onClick = onReminderWeekdayClick),
            )

            BalanceUpdateReminderPeriod.MONTHLY -> MoneyListRow(
                title = stringResource(R.string.account_reminder_month_day),
                trailing = stringResource(R.string.account_month_day_format, reminderConfig.monthDay),
                modifier = Modifier.clickable(onClick = onReminderMonthDayClick),
            )
        }
        MoneySectionDivider()
        MoneyListRow(
            title = stringResource(R.string.account_reminder_time),
            trailing = reminderConfig.timeText,
            modifier = Modifier.clickable(onClick = onReminderTimeClick),
        )
    }
}

@Composable
internal fun AccountVisualListRows(
    colorName: String,
    iconName: String,
    onColorClick: () -> Unit,
    onIconClick: () -> Unit,
) {
    MoneySectionDivider()
    MoneyListRow(
        title = stringResource(R.string.account_icon_title),
        trailing = accountIconLabel(iconName),
        leading = { AccountIconBadge(iconName = iconName, colorName = colorName, size = 28.dp, iconSize = 16.dp) },
        modifier = Modifier.clickable(onClick = onIconClick),
    )
    MoneySectionDivider()
    MoneyListRow(
        title = stringResource(R.string.account_color_title),
        trailing = accountColorLabel(colorName),
        leading = { AccountColorSwatch(colorName = colorName, size = 18.dp) },
        modifier = Modifier.clickable(onClick = onColorClick),
    )
}
