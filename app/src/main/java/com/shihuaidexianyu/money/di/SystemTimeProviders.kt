package com.shihuaidexianyu.money.di

import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import java.time.ZoneId

internal object SystemClockProvider : ClockProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

internal object SystemZoneIdProvider : ZoneIdProvider {
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
