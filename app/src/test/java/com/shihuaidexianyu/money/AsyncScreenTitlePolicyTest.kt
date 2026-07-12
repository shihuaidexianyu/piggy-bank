package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.balance.BalanceUpdateDetailUiState
import com.shihuaidexianyu.money.ui.balance.balanceUpdateDetailTitleRes
import com.shihuaidexianyu.money.ui.record.EditCashFlowUiState
import com.shihuaidexianyu.money.ui.record.editCashFlowTitleRes
import kotlin.test.assertEquals
import org.junit.Test

class AsyncScreenTitlePolicyTest {
    @Test
    fun `unresolved edit titles are neutral until data is committed`() {
        assertEquals(R.string.cash_flow_edit_title, editCashFlowTitleRes(EditCashFlowUiState()))
        assertEquals(R.string.balance_detail_neutral_title, balanceUpdateDetailTitleRes(BalanceUpdateDetailUiState()))
        assertEquals(
            R.string.cash_flow_edit_direction_format,
            editCashFlowTitleRes(
                EditCashFlowUiState(
                    isLoading = false,
                    direction = CashFlowDirection.OUTFLOW,
                ),
            ),
        )
        assertEquals(
            R.string.balance_detail_adjustment_title,
            balanceUpdateDetailTitleRes(BalanceUpdateDetailUiState(isLoading = false, delta = 1L)),
        )
    }
}
