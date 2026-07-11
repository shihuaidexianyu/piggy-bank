package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.ui.stats.StatsAccountCashFlowUiModel
import com.shihuaidexianyu.money.ui.stats.StatsDailyUiModel
import com.shihuaidexianyu.money.ui.stats.StatsScreen
import com.shihuaidexianyu.money.ui.stats.StatsTransferPathUiModel
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
        val path = HistoryRecordFilters(
            recordTypes = setOf(HistoryRecordType.TRANSFER),
            transferFromAccountId = 1L,
            transferToAccountId = 2L,
            dateStartAt = 1_000L,
            dateEndAt = 2_000L,
        )
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
                        netCashFlowHistoryFilters = monthCash,
                        dailyPoints = listOf(
                            StatsDailyUiModel(
                                date = LocalDate.of(2024, 3, 10),
                                dateText = "3月10日",
                                inflowText = "¥100.00",
                                outflowText = "¥40.00",
                                netFlowText = "+¥60.00",
                                historyFilters = monthCash.copy(dateStartAt = 1_100L, dateEndAt = 1_200L),
                            ),
                        ),
                        accountCashFlows = listOf(
                            StatsAccountCashFlowUiModel(1L, "隐藏账户", "¥100.00", "¥0.00", monthCash, monthCash),
                        ),
                        transferPaths = listOf(StatsTransferPathUiModel("隐藏账户 → 关闭账户", "¥70.00", path)),
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
        composeRule.onNodeWithText("每日趋势与净流").assertIsDisplayed()
        composeRule.onNodeWithText("账户现金流入／流出（转账单列）").assertIsDisplayed()
        composeRule.onNodeWithText("转账路径").assertIsDisplayed()
        composeRule.onNodeWithText("期初资产").assertDoesNotExist()
        composeRule.onNodeWithText("周").assertDoesNotExist()
        composeRule.onNodeWithText("年").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("下个月").assertIsNotEnabled()

        composeRule.onNodeWithText("净现金流").performClick()
        composeRule.onNodeWithText("隐藏账户 → 关闭账户").performClick()
        composeRule.runOnIdle { assertEquals(listOf(monthCash, path), opened) }
    }
}
