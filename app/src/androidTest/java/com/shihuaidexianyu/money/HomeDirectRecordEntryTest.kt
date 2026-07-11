package com.shihuaidexianyu.money

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeDirectRecordEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun incomeActionOpensFormDirectlyWithoutAccountPicker() {
        var selectedDirection: CashFlowDirection? = null
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        accountOptions = listOf(AccountOptionUiModel(id = 1, name = "现金")),
                    ),
                    onStartCashFlow = { direction -> selectedDirection = direction },
                    onStartTransfer = {},
                    onStartUpdateBalance = {},
                    onAllRemindersClick = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("入账").performClick()

        composeRule.runOnIdle {
            assertEquals(CashFlowDirection.INFLOW, selectedDirection)
        }
        composeRule.onNodeWithText("选择收入账户").assertDoesNotExist()
    }
}
