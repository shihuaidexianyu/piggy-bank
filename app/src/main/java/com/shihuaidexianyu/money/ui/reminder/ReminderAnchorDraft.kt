package com.shihuaidexianyu.money.ui.reminder

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.DateTimeParseException

internal data class ReminderAnchorInput(
    val anchorDueAt: Long,
    val periodValue: Int,
    val periodMonth: Int?,
)

private val anchorDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val anchorTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)

internal fun formatReminderAnchor(anchorDueAt: Long, zoneId: ZoneId): Pair<String, String> {
    val local = Instant.ofEpochMilli(anchorDueAt).atZone(zoneId)
    return local.toLocalDate().format(anchorDateFormatter) to local.toLocalTime().format(anchorTimeFormatter)
}

internal fun reminderAnchorToMillis(dateText: String, timeText: String, zoneId: ZoneId): Long? =
    runCatching {
        LocalDate.parse(dateText.trim(), anchorDateFormatter)
            .atTime(LocalTime.parse(timeText.trim(), anchorTimeFormatter))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

internal fun parseReminderAnchor(
    dateText: String,
    timeText: String,
    periodType: ReminderPeriodType,
    customDaysText: String,
    zoneId: ZoneId,
): Result<ReminderAnchorInput> = runCatching {
    val date = try {
        LocalDate.parse(dateText.trim(), anchorDateFormatter)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException(ValidationErrorText.REMINDER_ANCHOR_DATE_FORMAT)
    }
    val time = try {
        LocalTime.parse(timeText.trim(), anchorTimeFormatter)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException(ValidationErrorText.REMINDER_ANCHOR_TIME_FORMAT)
    }
    val anchorDueAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    when (periodType) {
        ReminderPeriodType.MONTHLY -> ReminderAnchorInput(anchorDueAt, date.dayOfMonth, null)
        ReminderPeriodType.YEARLY -> ReminderAnchorInput(anchorDueAt, date.dayOfMonth, date.monthValue)
        ReminderPeriodType.CUSTOM_DAYS -> {
            val customDays = customDaysText.toIntOrNull()
                ?: throw IllegalArgumentException(ValidationErrorText.REMINDER_INTERVAL_DAYS_INVALID)
            require(customDays in 1..3650) { ValidationErrorText.REMINDER_INTERVAL_DAYS_RANGE }
            ReminderAnchorInput(anchorDueAt, customDays, null)
        }
    }
}
