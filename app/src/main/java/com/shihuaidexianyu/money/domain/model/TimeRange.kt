package com.shihuaidexianyu.money.domain.model

/**
 * Half-open-closed epoch-millis range: [startAtMillis, endAtMillis].
 * `endAtMillis` is inclusive (startOfNextDay - 1).
 */
data class TimeRange(
    val startAtMillis: Long,
    val endAtMillis: Long,
)
