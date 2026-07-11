package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.TimeRange as DomainTimeRange

/**
 * Backward-compat alias. New code should depend on
 * [com.shihuaidexianyu.money.domain.model.TimeRange] directly.
 */
typealias TimeRange = DomainTimeRange

/**
 * Thin wrapper around [com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator].
 * Kept for UI/test callers; new domain code should use the calculator directly.
 */
object TimeRangeUtils {
    fun currentWeekRange(
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange = com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator.currentWeekRange(zoneId, nowMillis)

    fun currentMonthRange(
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange = com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator.currentMonthRange(zoneId, nowMillis)

    fun currentStatsRange(
        period: com.shihuaidexianyu.money.domain.model.StatsPeriod,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange = com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator.currentStatsRange(period, zoneId, nowMillis)

    fun statsRange(
        period: com.shihuaidexianyu.money.domain.model.StatsPeriod,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        anchorMillis: Long = System.currentTimeMillis(),
    ): TimeRange = com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator.statsRange(period, zoneId, anchorMillis)

    fun currentYearRange(
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TimeRange = com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator.currentYearRange(zoneId, nowMillis)
}
