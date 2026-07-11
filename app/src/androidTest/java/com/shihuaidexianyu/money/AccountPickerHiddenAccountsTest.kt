package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

class AccountPickerHiddenAccountsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hiddenAccountIsCollapsedThenVisibleAndStillSelectedAfterExpansion() {
        composeRule.setContent {
            MoneyTheme {
                AccountPickerDialog(
                    title = "选择账户",
                    accounts = listOf(
                        AccountOptionUiModel(id = 1L, name = "现金"),
                        AccountOptionUiModel(id = 2L, name = "备用金", isHidden = true),
                    ),
                    selectedAccountId = 2L,
                    onDismiss = {},
                    onPick = {},
                )
            }
        }

        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("备用金").assertDoesNotExist()
        composeRule.onNodeWithText("显示隐藏账户（1）").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("隐藏账户").assertIsDisplayed()
        composeRule.onNodeWithText("备用金").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已选择备用金").assertIsDisplayed()
        composeRule.onNodeWithText("收起隐藏账户").assertIsDisplayed()
    }
}
