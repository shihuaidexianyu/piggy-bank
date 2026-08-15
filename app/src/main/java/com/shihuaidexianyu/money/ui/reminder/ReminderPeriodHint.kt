package com.shihuaidexianyu.money.ui.reminder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import java.time.LocalDate

/**
 * Explains how the repeating schedule is derived from the first-due date: monthly reminders
 * repeat on the anchor's day of month (short months clamp to the month end), yearly reminders
 * on the anchor's month and day, and custom-day reminders every N days. Returns null when the
 * current draft cannot feed a meaningful hint (e.g. an unparseable date or blank interval).
 */
@Composable
internal fun reminderPeriodHint(
    periodType: ReminderPeriodType,
    anchorDateText: String,
    periodCustomDays: String,
): String? {
    val anchorDate = runCatching { LocalDate.parse(anchorDateText.trim()) }.getOrNull()
    return when (periodType) {
        ReminderPeriodType.MONTHLY -> anchorDate?.let {
            stringResource(R.string.reminder_period_monthly_hint, it.dayOfMonth)
        }
        ReminderPeriodType.YEARLY -> anchorDate?.let {
            stringResource(R.string.reminder_period_yearly_hint, it.monthValue, it.dayOfMonth)
        }
        ReminderPeriodType.CUSTOM_DAYS -> periodCustomDays.trim().toIntOrNull()?.let {
            stringResource(R.string.reminder_period_custom_days_hint, it)
        }
    }
}
