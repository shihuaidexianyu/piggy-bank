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
}
