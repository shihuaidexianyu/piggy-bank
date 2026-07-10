package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.util.concurrent.atomic.AtomicLong

private val testOperationSequence = AtomicLong()

internal fun testOperationId(): String =
    "test-operation-${testOperationSequence.incrementAndGet()}"

internal val testClockProvider = ClockProvider { 4_102_444_800_000L }
