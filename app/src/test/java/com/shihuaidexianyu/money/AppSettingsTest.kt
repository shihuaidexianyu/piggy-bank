package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import kotlin.test.assertEquals
import org.junit.Test

class SettingsNormalizationTest {
    @Test
    fun `currency symbol trims limits length and falls back to yuan`() {
        assertEquals("USDC", normalizeCurrencySymbol(" USDC符号 "))
        assertEquals("¥", normalizeCurrencySymbol("   "))
    }
}
