package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.widget.WidgetUpdateRequester
import org.junit.Test
import kotlin.test.assertEquals

class WidgetRefreshPolicyTest {
    @Test
    fun `one time widget refresh uses required debounce boundary`() {
        assertEquals(750L, WidgetUpdateRequester.DEBOUNCE_MILLIS)
        assertEquals("widget-balance-refresh", WidgetUpdateRequester.ONE_TIME_WORK_NAME)
    }
}
