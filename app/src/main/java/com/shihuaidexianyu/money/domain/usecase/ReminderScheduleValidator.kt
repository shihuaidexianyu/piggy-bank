package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import java.time.YearMonth

object ReminderScheduleValidator {
    /**
     * Reference year used to compute the maximum day for a given month. Intentionally a NON-leap
     * year so that Feb 29 is rejected — [ReminderNextDueCalculator] would otherwise silently clamp
     * it to Feb 28 in non-leap years, creating a mismatch between what the user configured and what
     * fires. Users wanting a yearly February reminder should pick Feb 1-28.
     */
    private const val VALIDATOR_REFERENCE_YEAR = 2023

    fun validate(
        periodType: ReminderPeriodType,
        periodValue: Int,
        periodMonth: Int?,
    ) {
        when (periodType) {
            ReminderPeriodType.MONTHLY -> {
                require(periodValue in 1..31) { "每月日期必须在 1 到 31 之间" }
            }

            ReminderPeriodType.YEARLY -> {
                val month = requireNotNull(periodMonth) { "请选择月份" }
                require(month in 1..12) { "月份必须在 1 到 12 之间" }
                val maxDay = YearMonth.of(VALIDATOR_REFERENCE_YEAR, month).lengthOfMonth()
                require(periodValue in 1..maxDay) { "该月份日期必须在 1 到 $maxDay 之间" }
            }

            ReminderPeriodType.CUSTOM_DAYS -> {
                require(periodValue >= 1) { "间隔天数必须大于 0" }
            }
        }
    }
}

