package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.ui.common.AccountColorOptions
import com.shihuaidexianyu.money.ui.common.AccountColorSwatch
import com.shihuaidexianyu.money.ui.common.accountVisualColor
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
            AccountColorChoiceDialog(
                selectedColorName = colorName,
                onSelect = {
                    onColorSelected(it)
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

/**
 * Icon picker as a scrollable 4-column grid: 19 options no longer fit the old full-width list
 * inside a dialog, and a grid shows every glyph at once instead of behind labels.
 */
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(AccountIconOptions, key = { it.name }) { option ->
                    val selected = option.name == normalizeAccountIconName(selectedIconName)
                    val label = stringResource(option.labelRes)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.name) }
                            .semantics { contentDescription = label }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (selected) 0.14f else 0.07f,
                                    ),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            // Text tokens for labels; selection is carried by the ring + tint.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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

/**
 * Color picker as a swatch grid — the previous text-only name list asked users to imagine the
 * colors it was offering. Each cell shows the real theme-resolved swatch; the selected one
 * carries a ring plus a contrast-safe check glyph, never color alone.
 */
@Composable
private fun AccountColorChoiceDialog(
    selectedColorName: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_color_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(AccountColorOptions, key = { it.name }) { option ->
                    val selected = option.name == normalizeAccountColorName(selectedColorName)
                    val label = stringResource(option.labelRes)
                    val swatchColor = accountVisualColor(option.name)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.name) }
                            .semantics { contentDescription = label }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = swatchColor,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(if (selected) 5.dp else 3.dp)
                                .background(color = swatchColor, shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    // Swatches passed the 3:1 floor against the surface, so
                                    // surface-colored glyphs stay legible on every swatch.
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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

/**
 * Account-kind selector (日常/投资). A single choice made once per account — deliberately not a
 * per-record category system: the kind reinterprets the account's whole ledger history at read
 * time (reconciliation deltas on 投资 accounts read as investment P&L).
 */
@Composable
internal fun AccountKindField(
    kind: AccountKind,
    onKindSelected: (AccountKind) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.account_kind_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    enabled = enabled,
                    onClick = { onKindSelected(option) },
                    label = { Text(accountKindLabel(option)) },
                )
            }
        }
        if (kind == AccountKind.INVESTMENT) {
            Text(
                text = stringResource(R.string.account_kind_investment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun accountKindLabel(kind: AccountKind): String = when (kind) {
    AccountKind.FUNDING -> stringResource(R.string.account_kind_funding)
    AccountKind.INVESTMENT -> stringResource(R.string.account_kind_investment)
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
