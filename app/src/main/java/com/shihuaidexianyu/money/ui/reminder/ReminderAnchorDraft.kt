package com.shihuaidexianyu.money.ui.reminder

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
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
        throw IllegalArgumentException("首次日期格式应为 YYYY-MM-DD")
    }
    val time = try {
        LocalTime.parse(timeText.trim(), anchorTimeFormatter)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("首次时间格式应为 HH:mm")
    }
    val anchorDueAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    when (periodType) {
        ReminderPeriodType.MONTHLY -> ReminderAnchorInput(anchorDueAt, date.dayOfMonth, null)
        ReminderPeriodType.YEARLY -> ReminderAnchorInput(anchorDueAt, date.dayOfMonth, date.monthValue)
        ReminderPeriodType.CUSTOM_DAYS -> {
            val customDays = customDaysText.toIntOrNull()
                ?: throw IllegalArgumentException("请输入有效的间隔天数")
            require(customDays in 1..3650) { "间隔天数必须在 1 到 3650 之间" }
            ReminderAnchorInput(anchorDueAt, customDays, null)
        }
    }
}
