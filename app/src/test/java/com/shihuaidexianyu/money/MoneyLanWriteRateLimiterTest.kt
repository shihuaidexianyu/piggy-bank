package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.lan.MoneyLanErrorCodes
import com.shihuaidexianyu.money.lan.MoneyLanProtocolException
import com.shihuaidexianyu.money.lan.MoneyLanWriteRateLimiter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Sliding-window behavior of the shared write limiter (design 6.4): sync.push charges its own
 * 10/min window on top of the general 60/min window, and a rejected push never consumes a
 * general slot (so replays of the rejection reason stay distinguishable).
 */
class MoneyLanWriteRateLimiterTest {
    private class Fixture {
        var now = 0L
        val limiter = MoneyLanWriteRateLimiter(nowMillis = { now })
    }

    @Test
    fun generalWindowAllowsSixtyWritesThenRateLimits() {
        val fixture = Fixture()
        repeat(MoneyLanWriteRateLimiter.MAX_WRITES_PER_WINDOW) { fixture.limiter.chargeWriteAction() }
        val error = assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeWriteAction() }
        assertEquals(MoneyLanErrorCodes.RATE_LIMITED, error.code)
        assertEquals("一分钟内写入次数过多，请稍后重试", error.message)
        Unit
    }

    @Test
    fun syncPushWindowCapsAtTenWhileGeneralWindowStillHasRoom() {
        val fixture = Fixture()
        repeat(MoneyLanWriteRateLimiter.MAX_SYNC_PUSHES_PER_WINDOW) { fixture.limiter.chargeSyncPush() }
        val error = assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeSyncPush() }
        assertEquals(MoneyLanErrorCodes.RATE_LIMITED, error.code)
        assertEquals("一分钟内同步推送次数过多，请稍后重试", error.message)
        // The general window is far from full: plain writes still succeed.
        fixture.limiter.chargeWriteAction()
        Unit
    }

    @Test
    fun syncPushChargesBothWindows() {
        val fixture = Fixture()
        // 55 general writes + 5 pushes fill the general window exactly.
        repeat(55) { fixture.limiter.chargeWriteAction() }
        repeat(5) { fixture.limiter.chargeSyncPush() }
        // The push window has room, but the general one is full — and the rejection must not
        // consume a push-window slot either.
        val error = assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeSyncPush() }
        assertEquals("一分钟内写入次数过多，请稍后重试", error.message)
        fixture.now += MoneyLanWriteRateLimiter.WINDOW_MILLIS
        fixture.limiter.chargeSyncPush()
        Unit
    }

    @Test
    fun windowSlidesAndFreesSlots() {
        val fixture = Fixture()
        repeat(MoneyLanWriteRateLimiter.MAX_WRITES_PER_WINDOW) { fixture.limiter.chargeWriteAction() }
        assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeWriteAction() }

        // Just before the window elapses the oldest entry still counts.
        fixture.now += MoneyLanWriteRateLimiter.WINDOW_MILLIS - 1
        assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeWriteAction() }

        fixture.now += 1
        repeat(MoneyLanWriteRateLimiter.MAX_WRITES_PER_WINDOW) { fixture.limiter.chargeWriteAction() }
        assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeWriteAction() }
        Unit
    }

    @Test
    fun rejectedPushDoesNotConsumeGeneralSlots() {
        val fixture = Fixture()
        repeat(MoneyLanWriteRateLimiter.MAX_SYNC_PUSHES_PER_WINDOW) { fixture.limiter.chargeSyncPush() }
        // 10 rejected pushes in a row...
        repeat(20) {
            assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeSyncPush() }
        }
        // ...leave the general window at exactly 10 used slots, so 50 more writes fit.
        repeat(MoneyLanWriteRateLimiter.MAX_WRITES_PER_WINDOW - 10) { fixture.limiter.chargeWriteAction() }
        val error = assertFailsWith<MoneyLanProtocolException> { fixture.limiter.chargeWriteAction() }
        assertTrue(error.code == MoneyLanErrorCodes.RATE_LIMITED)
        Unit
    }
}
