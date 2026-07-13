package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.ui.stats.StatsDailyUiModel
import com.shihuaidexianyu.money.ui.stats.StatsScreen
import com.shihuaidexianyu.money.ui.stats.StatsUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatsNaturalMonthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun analysisShowsOnlyMonthlyModulesAndRowsOpenTheirExactHistoryFilters() {
        val monthCash = HistoryRecordFilters(
            recordTypes = setOf(HistoryRecordType.CASH_FLOW),
            dateStartAt = 1_000L,
            dateEndAt = 2_000L,
        )
        val dayCash = monthCash.copy(dateStartAt = 1_100L, dateEndAt = 1_200L)
        val opened = mutableListOf<HistoryRecordFilters>()
        composeRule.setContent {
            MoneyTheme {
                StatsScreen(
                    state = StatsUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        hasSourceAccounts = true,
                        rangeText = "2024年3月",
                        canNavigateNext = false,
                        totalInflowText = "¥100.00",
                        totalOutflowText = "¥40.00",
                        netCashFlowText = "+¥60.00",
                        closingAssets = 25_000L,
                        closingAssetsText = "¥250.00",
                        netWorthGoalTargetAmount = 100_000L,
                        netWorthGoalTargetText = "¥1,000.00",
                        netWorthGoalDifferenceText = "¥750.00",
                        netCashFlowHistoryFilters = monthCash,
                        dailyPoints = listOf(
                            StatsDailyUiModel(
                                date = LocalDate.of(2024, 3, 10),
                                dateText = "3月10日",
                                inflowText = "¥100.00",
                                outflowText = "¥40.00",
                                netFlowText = "+¥60.00",
                                historyFilters = dayCash,
                                inflow = 10_000L,
                                outflow = 4_000L,
                                netFlow = 6_000L,
                                inflowHistoryFilters = dayCash.copy(amountDirection = HistoryAmountDirection.INCREASE),
                                outflowHistoryFilters = dayCash.copy(amountDirection = HistoryAmountDirection.DECREASE),
                            ),
                        ),
                    ),
                    onPreviousRange = {},
                    onNextRange = {},
                    onResetRange = {},
                    onOpenHistory = opened::add,
                )
            }
        }

        composeRule.onNodeWithText("分析").assertIsDisplayed()
        composeRule.onNodeWithText("2024年3月").assertIsDisplayed()
        composeRule.onNodeWithText("每日收支趋势").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("每日收支趋势图，共 1 天，其中 1 天有现金流").assertIsDisplayed()
        composeRule.onNodeWithText("累计净流").assertIsDisplayed()
        composeRule.onNodeWithText("账户现金流入／流出（转账单列）").assertDoesNotExist()
        composeRule.onNodeWithText("转账路径").assertDoesNotExist()
        composeRule.onNodeWithText("期初资产").assertIsDisplayed()
        composeRule.onNodeWithText("期末净资产").assertIsDisplayed()
        composeRule.onNodeWithText("目标 ¥1,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText("还差 ¥750.00").assertIsDisplayed()
        composeRule.onNodeWithText("周").assertDoesNotExist()
        composeRule.onNodeWithText("年").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("下个月").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("现金净额，+¥60.00").performClick()
        composeRule.onNodeWithContentDescription("3月10日，收入 ¥100.00，支出 ¥40.00，净额 +¥60.00").performClick()
        composeRule.runOnIdle { assertEquals(listOf(monthCash, dayCash), opened) }
    }
}
