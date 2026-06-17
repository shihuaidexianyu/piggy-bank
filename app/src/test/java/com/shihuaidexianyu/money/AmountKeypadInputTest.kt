package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.util.AmountKey
import com.shihuaidexianyu.money.util.appendAmountKey
import kotlin.test.assertEquals
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
        assertEquals("", appendAmountKey("", AmountKey.Plus))
        assertEquals("", appendAmountKey("", AmountKey.Minus))
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
