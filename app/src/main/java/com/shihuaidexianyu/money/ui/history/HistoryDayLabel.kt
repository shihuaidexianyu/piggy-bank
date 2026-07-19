package com.shihuaidexianyu.money.ui.history

import java.time.Instant
import java.time.ZoneId

/**
 * Classification of a history record's local day relative to "now", used to render friendly
 * sticky-header labels (今天 / 昨天 / M月d日 / yyyy年M月d日). Pure java.time logic with no
 * Android dependencies so it stays unit-testable on the JVM; zone handling follows the
 * `ZoneId.systemDefault()` convention of [com.shihuaidexianyu.money.util.DateTimeTextFormatter].
 */
sealed interface HistoryDayLabel {
    data object Today : HistoryDayLabel
    data object Yesterday : HistoryDayLabel
    data class SameYear(val month: Int, val dayOfMonth: Int) : HistoryDayLabel
    data class OtherYear(val year: Int, val month: Int, val dayOfMonth: Int) : HistoryDayLabel

    companion object {
        fun classify(
            occurredAt: Long,
            nowMillis: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): HistoryDayLabel {
            val day = Instant.ofEpochMilli(occurredAt).atZone(zoneId).toLocalDate()
            val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            return when (day) {
                today -> Today
                today.minusDays(1) -> Yesterday
                else -> if (day.year == today.year) {
                    SameYear(day.monthValue, day.dayOfMonth)
                } else {
                    OtherYear(day.year, day.monthValue, day.dayOfMonth)
                }
            }
        }
    }
}
