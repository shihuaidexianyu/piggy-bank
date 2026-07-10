package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.AmountKey
import com.shihuaidexianyu.money.util.appendAmountKey
import com.shihuaidexianyu.money.util.parseAmountKeypadPreview
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AmountKeypadInputTest {
    @Test
    fun `digits and decimal build amount text`() {
        var text = ""
        text = appendAmountKey(text, AmountKey.Digit(1))
        text = appendAmountKey(text, AmountKey.Digit(2))
        text = appendAmountKey(text, AmountKey.Decimal)
        text = appendAmountKey(text, AmountKey.Digit(3))

        assertEquals("12.3", text)
    }

    @Test
    fun `second decimal in current amount segment is ignored`() {
        assertEquals("12.3", appendAmountKey("12.3", AmountKey.Decimal))
        assertEquals("12+3.4", appendAmountKey("12+3.4", AmountKey.Decimal))
    }

    @Test
    fun `operator on empty input is ignored`() {
        assertEquals("", appendAmountKey("", AmountKey.Plus, allowSigned = false))
        assertEquals("", appendAmountKey("", AmountKey.Minus, allowSigned = false))
    }

    @Test
    fun `signed mode accepts leading minus and previews negative result`() {
        val text = appendAmountKey("", AmountKey.Minus, allowSigned = true) + "12.34"

        assertEquals("-12.34", text)
        assertEquals(-1_234L, parseAmountKeypadPreview(text, allowSigned = true))
    }

    @Test
    fun `unsigned mode rejects negative preview`() {
        assertNull(parseAmountKeypadPreview("-12.34", allowSigned = false))
    }

    @Test
    fun `consecutive operators are ignored`() {
        assertEquals("12+", appendAmountKey("12+", AmountKey.Minus))
        assertEquals("12-", appendAmountKey("12-", AmountKey.Plus))
    }

    @Test
    fun `delete removes the last character`() {
        assertEquals("12+", appendAmountKey("12+3", AmountKey.Delete))
        assertEquals("12", appendAmountKey("12+", AmountKey.Delete))
    }

    @Test
    fun `clear removes all input`() {
        assertEquals("", appendAmountKey("12+3", AmountKey.Clear))
    }
}
