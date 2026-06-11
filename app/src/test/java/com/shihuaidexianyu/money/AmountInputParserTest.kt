package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.AmountInputParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AmountInputParserTest {
    @Test
    fun `decimal amount converts to minor units`() {
        assertEquals(1234, AmountInputParser.parseToMinor("12.34"))
    }

    @Test
    fun `negative amount is rejected`() {
        assertNull(AmountInputParser.parseToMinor("-12.34"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(AmountInputParser.parseToMinor(""))
    }

    @Test
    fun `whitespace only returns null`() {
        assertNull(AmountInputParser.parseToMinor("   "))
    }

    @Test
    fun `integer input adds decimal places`() {
        assertEquals(1200, AmountInputParser.parseToMinor("12"))
    }

    @Test
    fun `zero returns zero`() {
        assertEquals(0, AmountInputParser.parseToMinor("0"))
    }

    @Test
    fun `extra decimal places are rejected`() {
        assertNull(AmountInputParser.parseToMinor("12.349"))
    }

    @Test
    fun `extra trailing zero decimal places are accepted`() {
        assertEquals(1234, AmountInputParser.parseToMinor("12.340"))
    }

    @Test
    fun `very large number returns null instead of crashing`() {
        assertNull(AmountInputParser.parseToMinor("99999999999999999.99"))
    }

    @Test
    fun `leading zeros are handled`() {
        assertEquals(750, AmountInputParser.parseToMinor("007.50"))
    }

    @Test
    fun `grouped amount with commas is accepted`() {
        assertEquals(1234567, AmountInputParser.parseToMinor("12,345.67"))
    }

    @Test
    fun `addition expression is evaluated`() {
        assertEquals(8310, AmountInputParser.parseToMinor("27.2+55.9"))
    }

    @Test
    fun `subtraction expression is evaluated`() {
        assertEquals(8765, AmountInputParser.parseToMinor("100-12.35"))
    }

    @Test
    fun `incomplete expression is rejected`() {
        assertNull(AmountInputParser.parseToMinor("12+"))
    }

    @Test
    fun `expression with too many effective decimal places is rejected`() {
        assertNull(AmountInputParser.parseToMinor("1.001+2"))
    }
}
