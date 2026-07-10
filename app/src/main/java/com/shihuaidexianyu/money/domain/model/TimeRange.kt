package com.shihuaidexianyu.money.domain.model

/** Half-open epoch-millis range: [startInclusive, endExclusive). */
data class TimeRange(
    val startInclusive: Long,
    val endExclusive: Long,
)
