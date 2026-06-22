package com.shihuaidexianyu.money.domain.model

import java.util.concurrent.TimeUnit

/**
 * Pure time-math helpers shared by domain use cases (e.g. [com.shihuaidexianyu.money.domain.usecase.LedgerBalanceCalculator]
 * and [com.shihuaidexianyu.money.domain.usecase.AccountRecordTimeValidator]) so that the domain layer
 * does not need to import from `util/`.
 */
object TimeMath {
    val MINUTE_MILLIS: Long = TimeUnit.MINUTES.toMillis(1)

    /**
     * Returns [timeMillis] rounded down to the start of its minute. Used to canonicalize
     * account-opening timestamps so that records occurring on the same minute as the
     * account's `createdAt` are treated as in-range.
     */
    fun floorToMinute(timeMillis: Long): Long {
        return timeMillis - Math.floorMod(timeMillis, MINUTE_MILLIS)
    }
}
