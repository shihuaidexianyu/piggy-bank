package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailUiState
import com.shihuaidexianyu.money.ui.balance.balanceUpdateDetailTitle
import com.shihuaidexianyu.money.ui.record.EditCashFlowUiState
import com.shihuaidexianyu.money.ui.record.editCashFlowTitle
import kotlin.test.assertEquals
import org.junit.Test

class AsyncScreenTitlePolicyTest {
    @Test
    fun `unresolved edit titles are neutral until data is committed`() {
        assertEquals("编辑收支记录", editCashFlowTitle(EditCashFlowUiState()))
        assertEquals("对账记录详情", balanceUpdateDetailTitle(BalanceUpdateDetailUiState()))
        assertEquals(
            "编辑出账",
            editCashFlowTitle(
                EditCashFlowUiState(
                    isLoading = false,
                    direction = CashFlowDirection.OUTFLOW,
                ),
            ),
        )
        assertEquals(
            "对账调整详情",
            balanceUpdateDetailTitle(BalanceUpdateDetailUiState(isLoading = false, delta = 1L)),
        )
    }
}
