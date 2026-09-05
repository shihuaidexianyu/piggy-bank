package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountItemUiModel
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsContent
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsUiState
import com.shihuaidexianyu.money.ui.accounts.moveAccountWithinGroup
import com.shihuaidexianyu.money.ui.accounts.orderGroup
import com.shihuaidexianyu.money.ui.accounts.sortAccountGroups
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReorderAccountsPresentationTest {
    @get:Rule val composeRule = createComposeRule()
    private var state by mutableStateOf(ReorderAccountsUiState())
    private var saves = 0
    private var exits = 0
    private fun account(id: Long, kind: AccountKind = AccountKind.FUNDING, hidden: Boolean = false) =
        ReorderAccountItemUiModel(id, "账户$id", "gray", "wallet", kind, id * 10000L, id, hidden)
    private val items = listOf(
        account(1), account(4, AccountKind.INVESTMENT), account(2), account(3),
        account(5, hidden = true), account(6, AccountKind.INVESTMENT, hidden = true),
    )

    private fun render(accounts: List<ReorderAccountItemUiModel> = items, fontScale: Float = 1f) {
        state = ReorderAccountsUiState(isLoading = false, accounts = accounts)
        fun update(updated: List<ReorderAccountItemUiModel>) {
            state = state.copy(accounts = updated, isDirty = updated != accounts)
        }
        fun moveByOne(id: Long, direction: Int) {
            val group = state.accounts.filter { it.orderGroup == state.accounts.first { a -> a.id == id }.orderGroup }
            group.getOrNull(group.indexOfFirst { it.id == id } + direction)?.let {
                update(moveAccountWithinGroup(state.accounts, id, it.id))
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MoneyTheme {
                    Box(Modifier.requiredSize(360.dp, 600.dp)) {
                        ReorderAccountsContent(
                            state = state,
                            onBack = { exits++ }, onSave = { saves++ },
                            onMove = { from, to -> update(moveAccountWithinGroup(state.accounts, from, to)) },
                            onMoveUp = { moveByOne(it, -1) }, onMoveDown = { moveByOne(it, 1) },
                            onSortByBalance = { update(sortAccountGroups(state.accounts, compareByDescending { it.balance })) },
                            onSortByRecent = {}, onSortByName = {},
                            onUndoChanges = { update(accounts) }, onRetry = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun groupingHiddenDisclosureAndSingleAccountHandleMatchDisplay() {
        render()
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
        composeRule.onNodeWithTag("account_order_handle_4", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("账户5").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("已隐藏"))
        composeRule.onNodeWithText("已隐藏").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("账户5"))
        composeRule.onNodeWithText("账户5").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("账户6"))
        composeRule.onNodeWithText("账户6").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("已隐藏"))
        composeRule.onNodeWithText("已隐藏").performClick()
        composeRule.onNodeWithText("账户5").assertDoesNotExist()
        composeRule.onNodeWithText("上移").assertDoesNotExist()
        composeRule.onNodeWithText("按余额").assertDoesNotExist()
    }

    @Test fun draggingFromFirstToLastStaysWithinGroupAndEnablesSave() {
        render()
        val handle = composeRule.onNodeWithTag("account_order_handle_1", useUnmergedTree = true)
        val from = handle.fetchSemanticsNode().boundsInRoot.center
        val to = composeRule.onNodeWithTag("account_order_row_3").fetchSemanticsNode().boundsInRoot.bottomCenter
        handle.performTouchInput { swipe(center, center + Offset(0f, to.y - from.y + 8f), durationMillis = 700) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf(2L, 4L, 3L, 1L, 5L, 6L), state.accounts.map { it.id }) }
        composeRule.onNodeWithText("保存").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
        composeRule.onNodeWithContentDescription("更多排序操作").performClick()
        composeRule.onNodeWithText("撤销本次调整").performClick()
        composeRule.runOnIdle { assertEquals(items, state.accounts) }
        composeRule.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test fun accessibilityMovesAndBackConfirmationPreserveDraft() {
        render()
        val actions = composeRule.onNodeWithTag("account_order_row_1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("下移"), actions.map { it.label })
        composeRule.runOnIdle { actions.single().action() }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.runOnIdle {
            assertEquals(0, exits)
            assertEquals(listOf(2L, 4L, 1L, 3L, 5L, 6L), state.accounts.map { it.id })
        }
    }

    @Test fun holdingHandleAtEdgeScrollsAndContinuesReordering() {
        render((1L..24L).map { account(it) })
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val handle = composeRule.onNodeWithTag("account_order_handle_1", useUnmergedTree = true)
        val from = handle.fetchSemanticsNode().boundsInRoot.center
        val bottom = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.bottom
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, bottom - from.y - 16f), delayMillis = 300)
        }
        composeRule.mainClock.advanceTimeBy(2200)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue("Held row should pass records initially outside the viewport", state.accounts.indexOfFirst { it.id == 1L } > 6)
            assertEquals((1L..24L).toSet(), state.accounts.map { it.id }.toSet())
        }
    }

    @Test fun largeTextKeepsSaveReachableAndAmountCompleteWhenScrolled() {
        render(fontScale = 2f)
        composeRule.onNodeWithText("账户3").performScrollTo()
        composeRule.onNodeWithText("¥300.00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.onNodeWithContentDescription("更多排序操作").performClick()
        composeRule.onNodeWithText("按余额").performClick()
        composeRule.onNodeWithText("保存").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }
}
