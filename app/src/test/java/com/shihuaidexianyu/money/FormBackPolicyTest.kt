package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.common.FormBackDecision
import com.shihuaidexianyu.money.ui.common.resolveFormBack
import kotlin.test.assertEquals
import org.junit.Test

class FormBackPolicyTest {
    @Test
    fun `pristine form exits while dirty form asks for discard confirmation`() {
        assertEquals(FormBackDecision.EXIT, resolveFormBack(isDirty = false))
        assertEquals(FormBackDecision.CONFIRM_DISCARD, resolveFormBack(isDirty = true))
    }
}
