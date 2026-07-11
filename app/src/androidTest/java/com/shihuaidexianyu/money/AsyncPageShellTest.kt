package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AsyncPageShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeEmptyKeepsTitleAndShowsOnlyCreateAccountContent() {
        var createClicks = 0
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                    ),
                    onStartUpdateBalance = {},
                    onAllRemindersClick = {},
                    onOpenSettings = {},
                    onCreateAccount = { createClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("创建第一个账户").assertIsDisplayed()
        composeRule.onNodeWithText("立即创建").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("本月").assertDoesNotExist()
        composeRule.onNodeWithText("快速记录").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, createClicks) }
    }

    @Test
    fun filteredHistoryEmptyKeepsSearchShellAndNamesFilteredEmpty() {
        composeRule.setContent {
            MoneyTheme {
                HistoryScreen(
                    state = HistoryUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        keyword = "午餐",
                    ),
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
                )
            }
        }

        composeRule.onNodeWithText("历史").assertIsDisplayed()
        composeRule.onNodeWithText("午餐").assertIsDisplayed()
        composeRule.onNodeWithText("没有符合筛选条件的记录").assertIsDisplayed()
        composeRule.onNodeWithText("排除关键词").assertDoesNotExist()
        composeRule.onNodeWithText("筛选 1").performClick()
        composeRule.onNodeWithText("排除关键词").assertIsDisplayed()
        composeRule.onNodeWithText("类型").assertIsDisplayed()
    }

    @Test
    fun invalidHistoryAmountIsShownAsAFieldErrorInAdvancedFilters() {
        composeRule.setContent {
            MoneyTheme {
                HistoryScreen(
                    state = HistoryUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        minAmountText = "999999999999999999999999",
                        minAmountError = "请输入有效金额",
                    ),
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
                )
            }
        }

        composeRule.onNodeWithText("筛选 1").performClick()
        composeRule.onNodeWithText("金额").performClick()
        composeRule.onNodeWithText("请输入有效金额").assertIsDisplayed()
    }
}
