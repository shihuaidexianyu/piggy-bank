package com.shihuaidexianyu.money.lan

/**
 * Shared sliding-window write limiter for the LAN server.
 *
 * The general window ([maxWritesPerWindow] per minute) is charged for every write action.
 * `sync.push` additionally charges its own pushes-per-minute window; both windows are checked
 * before either is charged so a rejected push never consumes a general slot. Callers charge AFTER
 * the idempotent-replay check, so replayed requests are free.
 */
class MoneyLanWriteRateLimiter(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxWritesPerWindow: Int = MAX_WRITES_PER_WINDOW,
    private val maxSyncPushesPerWindow: Int = MAX_SYNC_PUSHES_PER_WINDOW,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    private val lock = Any()
    private val writeTimestamps = ArrayDeque<Long>()
    private val syncPushTimestamps = ArrayDeque<Long>()

    /** Charges one general write action; throws RATE_LIMITED when the window is full. */
    fun chargeWriteAction() {
        synchronized(lock) {
            val now = nowMillis()
            prune(writeTimestamps, now)
            if (writeTimestamps.size >= maxWritesPerWindow) {
                throw MoneyLanProtocolException(
                    MoneyLanErrorCodes.RATE_LIMITED,
                    "一分钟内写入次数过多，请稍后重试",
                )
            }
            writeTimestamps.addLast(now)
        }
    }

    /** Charges one sync.push request against BOTH windows; throws RATE_LIMITED when either is full. */
    fun chargeSyncPush() {
        synchronized(lock) {
            val now = nowMillis()
            prune(writeTimestamps, now)
            prune(syncPushTimestamps, now)
            if (syncPushTimestamps.size >= maxSyncPushesPerWindow) {
                throw MoneyLanProtocolException(
                    MoneyLanErrorCodes.RATE_LIMITED,
                    "一分钟内同步推送次数过多，请稍后重试",
                )
            }
            if (writeTimestamps.size >= maxWritesPerWindow) {
                throw MoneyLanProtocolException(
                    MoneyLanErrorCodes.RATE_LIMITED,
                    "一分钟内写入次数过多，请稍后重试",
                )
            }
            writeTimestamps.addLast(now)
            syncPushTimestamps.addLast(now)
        }
    }

    private fun prune(timestamps: ArrayDeque<Long>, now: Long) {
        while (timestamps.firstOrNull()?.let { now - it >= windowMillis } == true) {
            timestamps.removeFirst()
        }
    }

    companion object {
        const val MAX_WRITES_PER_WINDOW = 60
        const val MAX_SYNC_PUSHES_PER_WINDOW = 10
        const val WINDOW_MILLIS = 60_000L
    }
}
