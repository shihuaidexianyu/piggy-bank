package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

/**
 * The account drill-down (`history/account/{accountId}`) reuses [HistoryScreen] with a locked
 * account: the title/back affordance replace the account filter entry points, and the locked
 * account is not presented as a user filter (no chip, not counted, not clearable).
 */
class HistoryScreenLockedModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun lockedState() = HistoryUiState(
        accountOptions = listOf(
            AccountOptionUiModel(id = 1L, name = "微信零钱"),
            AccountOptionUiModel(id = 2L, name = "招商银行"),
        ),
        selectedAccountId = 1L,
        isLoading = false,
        hasCommittedContent = true,
    )

    private fun renderHistory(lockedAccountId: Long?) {
        composeRule.setContent {
            MoneyTheme {
                HistoryScreen(
                    state = lockedState(),
                    onKeywordChange = {},
                    onExcludeKeywordChange = {},
                    onRecordTypesChange = {},
                    onAccountChange = {},
                    onDateRangeChange = { _, _ -> },
                    onMinAmountChange = {},
                    onMaxAmountChange = {},
                    onAmountDirectionChange = {},
                    onClearAllFilters = {},
                    onLoadMore = {},
                    onRecordClick = {},
                    lockedAccountId = lockedAccountId,
                    onBack = if (lockedAccountId != null) ({}) else null,
                )
            }
        }
    }

    @Test
    fun lockedModeShowsAccountTitleAndBackAndHidesAccountFilterEntries() {
        renderHistory(lockedAccountId = 1L)

        // Title is the account name — and appears exactly once (no account filter chip).
        composeRule.onAllNodesWithText("微信零钱").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()

        // The locked account is not counted as a filter: plain "筛选", no 清除 button.
        composeRule.onNodeWithText("筛选").assertIsDisplayed()
        composeRule.onNodeWithText("清除").assertDoesNotExist()

        // The filter sheet has no account row.
        composeRule.onNodeWithText("筛选").performClick()
        composeRule.onNodeWithText("类型").assertIsDisplayed()
        composeRule.onNodeWithText("账户").assertDoesNotExist()
    }

    @Test
    fun unlockedTabKeepsAccountFilterEntries() {
        renderHistory(lockedAccountId = null)

        // Tab title, no back button, account chip visible, 清除 visible.
        composeRule.onNodeWithText("明细").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertDoesNotExist()
        composeRule.onNodeWithText("微信零钱").assertIsDisplayed()
        composeRule.onNodeWithText("清除").assertIsDisplayed()

        composeRule.onNodeWithText("筛选 1").assertIsDisplayed()
        composeRule.onNodeWithText("筛选 1").performClick()
        composeRule.onNodeWithText("账户").assertIsDisplayed()
    }
}
