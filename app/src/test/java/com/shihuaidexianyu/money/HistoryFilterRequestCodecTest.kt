package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.navigation.HistoryFilterRequestCodec
import com.shihuaidexianyu.money.navigation.HistoryFilterNavigationRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class HistoryFilterRequestCodecTest {
    @Test
    fun `analysis down-drill filter round trips without broadening`() {
        val request = HistoryRecordFilters(
            keyword = "工资 & 100%_",
            excludeKeyword = "内部/冲销",
            recordTypes = setOf(HistoryRecordType.CASH_FLOW, HistoryRecordType.TRANSFER),
            accountId = 9L,
            transferFromAccountId = 9L,
            transferToAccountId = 10L,
            dateStartAt = 1_000L,
            dateEndAt = 2_000L,
            minAmount = 123L,
            maxAmount = 456L,
            amountDirection = HistoryAmountDirection.DECREASE,
        )

        val encoded = HistoryFilterRequestCodec.encode(request)
        val decoded = HistoryFilterRequestCodec.decode(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `malformed down-drill payload fails closed instead of dropping filters`() {
        listOf(
            "types=CASH_FLOW,UNKNOWN",
            "accountId=not-a-long",
            "accountId=0",
            "transferFrom=0",
            "transferFrom=1&transferTo=1",
            "start=9223372036854775808",
            "start=-1&end=100",
            "start=100&end=100",
            "start=200&end=100",
            "min=-1",
            "min=200&max=100",
            "direction=SIDEWAYS",
            "unknown=value",
        ).forEach { payload ->
            assertFailsWith<IllegalArgumentException>(payload) {
                HistoryFilterRequestCodec.decode(payload)
            }
        }
    }

    @Test
    fun `repeated identical drill-downs carry distinct tokens and decode exactly once per event value`() {
        val filters = HistoryRecordFilters(
            recordTypes = setOf(HistoryRecordType.TRANSFER),
            transferFromAccountId = 1L,
            transferToAccountId = 2L,
            dateStartAt = 100L,
            dateEndAt = 200L,
        )

        val first = HistoryFilterNavigationRequest.create(filters, token = "first")
        val second = HistoryFilterNavigationRequest.create(filters, token = "second")

        assertTrue(first != second)
        assertEquals(filters, HistoryFilterNavigationRequest.decode(first))
        assertEquals(filters, HistoryFilterNavigationRequest.decode(second))
        assertFailsWith<IllegalArgumentException> { HistoryFilterNavigationRequest.decode("missing-token") }
    }
}
