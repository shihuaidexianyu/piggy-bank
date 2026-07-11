package com.shihuaidexianyu.money.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun clockMinuteTickerFlow(clockProvider: ClockProvider): Flow<Long> = flow {
    while (true) {
        val now = clockProvider.nowMillis()
        emit(now)
        delay((60_000L - Math.floorMod(now, 60_000L)).coerceAtLeast(1L))
    }
}
