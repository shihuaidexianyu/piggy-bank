package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.util.SharedTextAmountExtractor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedTextAmountExtractorTest {
    @Test
    fun `parses grouped and full width currency with Chinese direction keywords`() {
        val outflow = SharedTextAmountExtractor.parse("微信支付支出 ￥１，２３４．５６ 元")
        val income = SharedTextAmountExtractor.parse("工资到账 ¥12,345.67")

        assertEquals(123_456L, outflow.amountInMinor)
        assertEquals(CashFlowDirection.OUTFLOW, outflow.direction)
        assertEquals(1_234_567L, income.amountInMinor)
        assertEquals(CashFlowDirection.INFLOW, income.direction)
    }

    @Test
    fun `dates order ids and phone numbers are not selected as money`() {
        val result = SharedTextAmountExtractor.parse(
            "日期 2026-07-11，订单号 2026071100123456，手机号 13800138000",
        )

        assertNull(result.amountInMinor)
        assertTrue(result.isUncertain)
    }

    @Test
    fun `different equally plausible monetary candidates remain ambiguous`() {
        val result = SharedTextAmountExtractor.parse("支付 ¥12.00，另支付 ¥18.00")

        assertNull(result.amountInMinor)
        assertTrue(result.isAmbiguous)
        assertEquals(listOf(1_200L, 1_800L), result.candidates.map { it.amountInMinor })
    }

    @Test
    fun `non positive and overflowing candidates are rejected safely`() {
        assertNull(SharedTextAmountExtractor.parse("支付 ¥0.00").amountInMinor)
        assertNull(SharedTextAmountExtractor.parse("支付 -¥12.00").amountInMinor)
        assertNull(
            SharedTextAmountExtractor.parse("支付 ¥999999999999999999999999.99").amountInMinor,
        )
    }
}
