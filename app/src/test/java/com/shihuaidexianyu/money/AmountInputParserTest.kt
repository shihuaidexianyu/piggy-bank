package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.AmountInputParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AmountInputParserTest {
    @Test
    fun `signed parser accepts negative amount`() {
        assertEquals(-1_234L, AmountInputParser.parseSignedToMinor("-12.34"))
    }

    @Test
    fun `signed parser accepts arithmetic yielding negative amount`() {
        assertEquals(-500L, AmountInputParser.parseSignedToMinor("5-10"))
    }

    @Test
    fun `unsigned parser rejects negative amount`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("-12.34"))
    }

    @Test
    fun `decimal amount converts to minor units`() {
        assertEquals(1234, AmountInputParser.parseUnsignedToMinor("12.34"))
    }

    @Test
    fun `negative amount is rejected`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("-12.34"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(AmountInputParser.parseUnsignedToMinor(""))
    }

    @Test
    fun `whitespace only returns null`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("   "))
    }

    @Test
    fun `integer input adds decimal places`() {
        assertEquals(1200, AmountInputParser.parseUnsignedToMinor("12"))
    }

    @Test
    fun `zero returns zero`() {
        assertEquals(0, AmountInputParser.parseUnsignedToMinor("0"))
    }

    @Test
    fun `extra decimal places are rejected`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("12.349"))
    }

    @Test
    fun `extra trailing zero decimal places are accepted`() {
        assertEquals(1234, AmountInputParser.parseUnsignedToMinor("12.340"))
    }

    @Test
    fun `very large number returns null instead of crashing`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("99999999999999999.99"))
    }

    @Test
    fun `leading zeros are handled`() {
        assertEquals(750, AmountInputParser.parseUnsignedToMinor("007.50"))
    }

    @Test
    fun `grouped amount with commas is accepted`() {
        assertEquals(1234567, AmountInputParser.parseUnsignedToMinor("12,345.67"))
    }

    @Test
    fun `addition expression is evaluated`() {
        assertEquals(8310, AmountInputParser.parseUnsignedToMinor("27.2+55.9"))
    }

    @Test
    fun `subtraction expression is evaluated`() {
        assertEquals(8765, AmountInputParser.parseUnsignedToMinor("100-12.35"))
    }

    @Test
    fun `incomplete expression is rejected`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("12+"))
    }

    @Test
    fun `expression with too many effective decimal places is rejected`() {
        assertNull(AmountInputParser.parseUnsignedToMinor("1.001+2"))
    }
}
