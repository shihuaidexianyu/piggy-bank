package com.shihuaidexianyu.money.domain.model

enum class StatsPeriod(
    val value: String,
    val displayName: String,
) {
    WEEK("week", "周"),
    MONTH("month", "月"),
    YEAR("year", "年"),
}
