package com.shihuaidexianyu.money.domain.model

enum class StatsPeriod(
    val value: String,
    val displayName: String,
) {
    WEEK("week", "本周"),
    MONTH("month", "本月"),
    YEAR("year", "今年"),
}
