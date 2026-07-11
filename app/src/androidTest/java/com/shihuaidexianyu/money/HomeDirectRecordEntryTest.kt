package com.shihuaidexianyu.money

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

class HomeDirectRecordEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeDoesNotDuplicateRootOwnedLedgerActions() {
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        hasAnyAccounts = true,
                        accountOptions = listOf(AccountOptionUiModel(id = 1, name = "现金")),
                    ),
                    onStartUpdateBalance = {},
                    onAllRemindersClick = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("快速记录").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("入账").assertDoesNotExist()
        composeRule.onNodeWithText("选择收入账户").assertDoesNotExist()
    }
}
