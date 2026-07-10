package com.shihuaidexianyu.money.domain.time

class MutationTimestampOverflowException : IllegalStateException("更新时间已达到上限")

fun nextMutationTimestamp(now: Long, previous: Long): Long {
    if (now > previous) return now
    if (previous == Long.MAX_VALUE) throw MutationTimestampOverflowException()
    return previous + 1L
}
