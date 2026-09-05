package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.HistoryScreen
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryPresentationTest {
    @get:Rule val composeRule = createComposeRule()
    private var lastKeyword = ""
    private var scrolled = false
    private var openedRecord: String? = null
    private val record = HistoryRecordUiModel(
        id = "cash_1", recordId = 1L, kind = HistoryRecordKind.CASH_FLOW,
        title = "午餐", subtitle = "招商银行", amount = -3_800L,
        occurredAt = LocalDate.now().minusDays(3).atTime(14, 35).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        accountIds = setOf(1L), keywordSource = "午餐", primaryAccountId = 1L,
        balanceBefore = 123_400L, balanceAfter = 119_600L,
    )

    private fun render(records: List<HistoryRecordUiModel> = listOf(record), fontScale: Float = 1f) {
        composeRule.setContent {
            var state by remember { mutableStateOf(HistoryUiState(records = records, isLoading = false, hasCommittedContent = true)) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MoneyTheme {
                    Box(Modifier.requiredSize(360.dp, 600.dp)) {
                        HistoryScreen(
                            state = state,
                            onKeywordChange = { lastKeyword = it; state = state.copy(keyword = it) },
                            onExcludeKeywordChange = {}, onRecordTypesChange = {}, onAccountChange = {},
                            onDateRangeChange = { _, _ -> }, onMinAmountChange = {}, onMaxAmountChange = {},
                            onAmountDirectionChange = {}, onClearAllFilters = {}, onLoadMore = {},
                            onRecordClick = { openedRecord = it.id }, onScrolledChange = { scrolled = it },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun rowsShowOnlyTimeAndDetailsRetainBalanceEvidenceAndEditEntry() {
        render()
        composeRule.onNodeWithText("招商银行 · 14:35", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("余额变化").assertDoesNotExist()
        composeRule.onNodeWithTag("history_row_cash_1").performClick()
        composeRule.onNodeWithText("账目详情").assertIsDisplayed()
        composeRule.onNodeWithText("余额变化").assertIsDisplayed()
        composeRule.onNodeWithText("修改记录").performClick()
        composeRule.runOnIdle { assertEquals("cash_1", openedRecord) }
    }

    @Test
    fun searchIsDisclosedAndClosingClearsOnlyKeyword() {
        render()
        composeRule.onNodeWithTag("history_search_field").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("搜索账目").performClick()
        composeRule.onNodeWithTag("history_search_field").performTextInput("午餐")
        composeRule.runOnIdle { assertEquals("午餐", lastKeyword) }
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("history_search_field").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("", lastKeyword) }
    }

    @Test
    fun interruptedSearchAnimationKeepsDateAndRecordsTogether() {
        render()
        val header = composeRule.onNode(SemanticsMatcher("date header") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("history_date_header_") == true
        })
        val row = composeRule.onNodeWithTag("history_row_cash_1")
        val originalHeader = header.fetchSemanticsNode().boundsInRoot
        val originalRow = row.fetchSemanticsNode().boundsInRoot
        fun assertMovesTogether() {
            val headerDelta = header.fetchSemanticsNode().boundsInRoot.top - originalHeader.top
            val rowDelta = row.fetchSemanticsNode().boundsInRoot.top - originalRow.top
            assertEquals("Date and record must move as one list", headerDelta, rowDelta, 1.5f)
        }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithContentDescription("搜索账目").performClick()
            repeat(3) {
                composeRule.mainClock.advanceTimeBy(32)
                composeRule.waitForIdle()
                assertMovesTogether()
            }
            composeRule.onNodeWithContentDescription("关闭搜索").performClick()
            repeat(3) {
                composeRule.mainClock.advanceTimeBy(32)
                composeRule.waitForIdle()
                assertMovesTogether()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("history_search_field").assertDoesNotExist()
            assertEquals(originalRow.top, row.fetchSemanticsNode().boundsInRoot.top, 1.5f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun openingAndClosingSearchWhileScrolledPreservesReadingPosition() {
        render((1..30).map { record.copy(id = "cash_$it", recordId = it.toLong()) })
        composeRule.onNode(hasScrollAction()).performScrollToIndex(16)
        val row = composeRule.onNodeWithTag("history_row_cash_16")
        val originalTop = row.fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithContentDescription("搜索账目").performClick()
        composeRule.onNodeWithTag("history_search_field").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("history_row_cash_1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("关闭搜索").performClick()
        composeRule.onNodeWithTag("history_search_field").assertDoesNotExist()
        row.assertIsDisplayed()
        assertEquals(originalTop, row.fetchSemanticsNode().boundsInRoot.top, 1.5f)
        composeRule.runOnIdle { assertTrue(scrolled) }
    }

    @Test
    fun scrollingReportsCompactActionAndRestoresItAtTop() {
        render((1..30).map { record.copy(id = "cash_$it", recordId = it.toLong()) })
        composeRule.runOnIdle { assertFalse(scrolled) }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("history_row_cash_20"))
        composeRule.runOnIdle { assertTrue(scrolled) }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.runOnIdle { assertFalse(scrolled) }
    }

    @Test
    fun largeTextKeepsFullAmountAndTimeVisible() {
        render(fontScale = 2f)
        composeRule.onNodeWithText("-¥38.00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("招商银行 · 14:35", useUnmergedTree = true).assertIsDisplayed()
    }
}
