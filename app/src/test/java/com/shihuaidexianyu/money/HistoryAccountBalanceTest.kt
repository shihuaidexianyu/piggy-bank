package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.historyAccountBalanceAfter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class HistoryAccountBalanceTest {
    private val transfer = HistoryRecordUiModel(
        id = "transfer_1", recordId = 1L, kind = HistoryRecordKind.TRANSFER,
        title = "转账", subtitle = "工资卡 → 零钱", amount = 500L, occurredAt = 1L,
        accountIds = setOf(2L, 1L), keywordSource = "转账", primaryAccountId = 1L,
        balanceBefore = 1_000L, balanceAfter = 500L,
        relatedBalanceBefore = 0L, relatedBalanceAfter = 500L,
    )

    @Test
    fun `receiving account balance uses receiving side without depending on set order`() {
        val record = transfer.copy(relatedBalanceAfter = 1_500L)
        assertEquals(500L, historyAccountBalanceAfter(record, 1L))
        assertEquals(1_500L, historyAccountBalanceAfter(record, 2L))
        assertNull(historyAccountBalanceAfter(record, 3L))
    }

    @Test
    fun `missing transfer source identity does not guess which balance to show`() {
        assertNull(historyAccountBalanceAfter(transfer.copy(primaryAccountId = null), 2L))
    }
}
