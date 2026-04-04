package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlin.test.assertEquals
import org.junit.Test

class AmountFormatterTest {
    @Test
    fun `formats amount with grouping separators`() {
        assertEquals(
            "¥12,345.67",
            AmountFormatter.format(1_234_567, AppSettings()),
        )
    }

    @Test
    fun `formats negative amount with grouping separators`() {
        assertEquals(
            "-¥12,345.67",
            AmountFormatter.format(-1_234_567, AppSettings()),
        )
    }

    @Test
    fun `formats plain amount with grouping separators`() {
        assertEquals(
            "12,345.67",
            AmountFormatter.formatPlain(1_234_567),
        )
    }

    @Test
    fun `formats long min value without duplicated minus sign`() {
        assertEquals(
            "-¥92,233,720,368,547,758.08",
            AmountFormatter.format(Long.MIN_VALUE, AppSettings()),
        )
    }
}

