package com.shihuaidexianyu.money.domain.model

/**
 * The period the home dashboard summarizes. Selecting a period changes the income/expense
 * aggregates, the comparison baseline, and the net-worth trend granularity.
 */
enum class DashboardPeriod {
    WEEK,
    MONTH,
    YEAR,
    ;

    companion object {
        val DEFAULT: DashboardPeriod = MONTH

        fun fromName(value: String?): DashboardPeriod =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
