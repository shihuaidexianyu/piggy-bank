package com.shihuaidexianyu.money

import java.util.concurrent.atomic.AtomicLong

private val testOperationSequence = AtomicLong()

internal fun testOperationId(): String =
    "test-operation-${testOperationSequence.incrementAndGet()}"
