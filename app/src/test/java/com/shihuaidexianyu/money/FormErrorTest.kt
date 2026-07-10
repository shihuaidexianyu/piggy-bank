package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.common.FormError
import com.shihuaidexianyu.money.ui.common.toFormError
import kotlin.test.assertEquals
import org.junit.Test

class FormErrorTest {
    @Test
    fun `maps missing account message to MissingField-ACCOUNT`() {
        val error = IllegalArgumentException("请选择账户").toFormError()
        assertEquals(FormError.MissingField(FormError.Field.ACCOUNT), error)
    }

    @Test
    fun `maps missing amount message to MissingField-AMOUNT`() {
        val error = IllegalArgumentException("金额不能为空").toFormError()
        assertEquals(FormError.MissingField(FormError.Field.AMOUNT), error)
    }

    @Test
    fun `maps amount must be positive to AmountMustBePositive`() {
        val error = IllegalArgumentException("金额必须大于 0").toFormError()
        assertEquals(FormError.AmountMustBePositive, error)
    }

    @Test
    fun `maps invalid amount prefix to InvalidAmount`() {
        val error = IllegalArgumentException("请输入有效金额，最多保留两位小数").toFormError()
        assertEquals(FormError.InvalidAmount, error)
    }

    @Test
    fun `maps future timestamp to FutureTimestamp`() {
        val error = IllegalArgumentException("时间不能晚于当前时间").toFormError()
        assertEquals(FormError.FutureTimestamp, error)
    }

    @Test
    fun `maps duplicate name to DuplicateName`() {
        val error = IllegalArgumentException("已存在同名账户").toFormError()
        assertEquals(FormError.DuplicateName, error)
    }

    @Test
    fun `maps closed account message to ClosedAccount with action suffix`() {
        val error = IllegalArgumentException("关闭账户不能修改账户").toFormError()
        assertEquals(FormError.ClosedAccount("修改账户"), error)
    }

    @Test
    fun `maps not-found suffix to NotFound`() {
        val error = IllegalArgumentException("账户不存在").toFormError()
        assertEquals(FormError.NotFound("账户"), error)
    }

    @Test
    fun `maps same transfer accounts to SameTransferAccounts`() {
        val error = IllegalArgumentException("请选择不同的转出和转入账户").toFormError()
        assertEquals(FormError.SameTransferAccounts, error)
    }

    @Test
    fun `unknown message falls through to Unknown`() {
        val error = IllegalArgumentException("数据库炸了").toFormError()
        assertEquals(FormError.Unknown("数据库炸了"), error)
    }

    @Test
    fun `null message falls through to Unknown with fallback`() {
        val error = RuntimeException().toFormError()
        assertEquals(FormError.Unknown("操作失败"), error)
    }
}
