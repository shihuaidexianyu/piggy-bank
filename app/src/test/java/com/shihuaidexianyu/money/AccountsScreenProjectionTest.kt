package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.accounts.AccountListItemUiModel
import com.shihuaidexianyu.money.ui.accounts.AccountsUiState
import com.shihuaidexianyu.money.ui.accounts.shouldShowAccountOverview
import kotlin.test.assertTrue
import org.junit.Test

class AccountsScreenProjectionTest {
    @Test
    fun `overview remains visible with no open account and one nonzero closed account`() {
        val state = AccountsUiState(
            openAccounts = emptyList(),
            closedAccounts = listOf(
                AccountListItemUiModel(
                    id = 1L,
                    name = "已关闭账户",
                    colorName = "blue",
                    iconName = "wallet",
                    balance = 12_345L,
                    isClosed = true,
                    isStale = false,
                    displayOrder = 0,
                ),
            ),
        )

        assertTrue(shouldShowAccountOverview(state))
    }
}
