package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.launch.AppLaunchDestination
import com.shihuaidexianyu.money.domain.launch.AppLaunchInput
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLaunchRequestFactoryTest {
    @Test
    fun `only validated shortcut and identity payloads are accepted`() {
        assertNull(AppLaunchRequestFactory.create("a", AppLaunchInput.Shortcut("unknown")))
        assertNull(AppLaunchRequestFactory.create("b", AppLaunchInput.BalanceNotification(0L)))
        assertNull(AppLaunchRequestFactory.create("c", AppLaunchInput.RecurringNotification(1L, -1L)))
        assertNull(AppLaunchRequestFactory.create("c0", AppLaunchInput.RecurringNotification(1L, 0L)))
        assertEquals(
            AppLaunchDestination.BatchReconcile,
            AppLaunchRequestFactory.create(
                "ok",
                AppLaunchInput.Shortcut("balance_check"),
            )?.destination,
        )
    }

    @Test
    fun `share payload rejects blank and excessive text without parsing sensitive amount`() {
        assertNull(AppLaunchRequestFactory.create("blank", AppLaunchInput.SharedText("  ")))
        assertNull(
            AppLaunchRequestFactory.create(
                "large",
                AppLaunchInput.SharedText("x".repeat(4_001)),
            ),
        )
        assertEquals(
            AppLaunchDestination.SharePreview("支出 ￥１，２３４．５６"),
            AppLaunchRequestFactory.create(
                "share",
                AppLaunchInput.SharedText("支出 ￥１，２３４．５６"),
            )?.destination,
        )
    }
}
