package com.shihuaidexianyu.money.ui.reminder

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.usecase.ReminderScheduleValidator
import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText

internal data class ReminderScheduleInput(
    val periodValue: Int,
    val periodMonth: Int?,
)

internal fun parseReminderScheduleInput(
    periodType: ReminderPeriodType,
    periodDayText: String,
    periodMonthText: String,
    periodCustomDaysText: String,
): Result<ReminderScheduleInput> = runCatching {
    when (periodType) {
        ReminderPeriodType.MONTHLY -> {
            val periodValue = periodDayText.toIntOrNull()
                ?: throw IllegalArgumentException(ValidationErrorText.REMINDER_MONTH_DAY_INVALID)
            ReminderScheduleValidator.validate(periodType, periodValue, null)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = null)
        }

        ReminderPeriodType.YEARLY -> {
            val periodMonth = periodMonthText.toIntOrNull()
                ?: throw IllegalArgumentException(ValidationErrorText.REMINDER_MONTH_INVALID)
            val periodValue = periodDayText.toIntOrNull()
                ?: throw IllegalArgumentException(ValidationErrorText.REMINDER_DAY_INVALID)
            ReminderScheduleValidator.validate(periodType, periodValue, periodMonth)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = periodMonth)
        }

        ReminderPeriodType.CUSTOM_DAYS -> {
            val periodValue = periodCustomDaysText.toIntOrNull()
                ?: throw IllegalArgumentException(ValidationErrorText.REMINDER_INTERVAL_DAYS_INVALID)
            ReminderScheduleValidator.validate(periodType, periodValue, null)
            ReminderScheduleInput(periodValue = periodValue, periodMonth = null)
        }
    }
}
