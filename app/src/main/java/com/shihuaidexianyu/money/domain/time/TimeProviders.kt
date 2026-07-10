package com.shihuaidexianyu.money.domain.time

import java.time.ZoneId

fun interface ClockProvider {
    fun nowMillis(): Long
}

fun interface ZoneIdProvider {
    fun zoneId(): ZoneId
}
