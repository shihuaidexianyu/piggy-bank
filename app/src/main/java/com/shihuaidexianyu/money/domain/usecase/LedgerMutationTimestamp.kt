package com.shihuaidexianyu.money.domain.usecase

internal fun nextLedgerMutationTimestamp(now: Long, storedUpdatedAt: Long): Long {
    if (now > storedUpdatedAt) return now
    check(storedUpdatedAt != Long.MAX_VALUE) { "记录更新时间已达到上限" }
    return storedUpdatedAt + 1
}
