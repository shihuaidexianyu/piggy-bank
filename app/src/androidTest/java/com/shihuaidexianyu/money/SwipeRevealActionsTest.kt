package com.shihuaidexianyu.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.SwipeRevealAction
import com.shihuaidexianyu.money.ui.common.SwipeRevealActionsBox
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeRevealActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullSwipesOnlyRevealActionsUntilTheirButtonsAreTapped() {
        var deleteCount = 0
        var editCount = 0
        setSwipeContent(
            onDelete = { deleteCount += 1 },
            onEdit = { editCount += 1 },
        )

        composeRule.onNodeWithTag(SWIPE_TAG).performTouchInput { swipeLeft(durationMillis = 100) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, deleteCount)
            assertEquals(0, editCount)
        }
        composeRule.onNodeWithText("删除").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, deleteCount) }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SWIPE_TAG).performTouchInput { swipeRight(durationMillis = 100) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, deleteCount)
            assertEquals(0, editCount)
        }
        composeRule.onNodeWithText("编辑").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, editCount) }
    }

    @Test
    fun reversingBeforeReleaseNeverDispatchesAnAction() {
        var deleteCount = 0
        setSwipeContent(onDelete = { deleteCount += 1 })

        composeRule.onNodeWithTag(SWIPE_TAG).performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.9f, centerY))
            up()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(0, deleteCount) }
    }

    @Test
    fun verticalDominantDragNeverRevealsActions() {
        var deleteCount = 0
        setSwipeContent(onDelete = { deleteCount += 1 })

        // List-scroll drift: strong vertical motion with a slight sideways component.
        composeRule.onNodeWithTag(SWIPE_TAG).performTouchInput {
            swipe(
                start = Offset(width * 0.5f, centerY),
                end = Offset(width * 0.4f, centerY - 600f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("删除").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, deleteCount) }
    }

    @Test
    fun slightlyDiagonalHorizontalSwipeStillRevealsActions() {
        var deleteCount = 0
        setSwipeContent(onDelete = { deleteCount += 1 })

        // Imperfect real-world swipe: mostly horizontal, some vertical drift.
        composeRule.onNodeWithTag(SWIPE_TAG).performTouchInput {
            swipe(
                start = Offset(width * 0.9f, centerY),
                end = Offset(width * 0.1f, centerY - 120f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("删除").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, deleteCount) }
    }

    private fun setSwipeContent(
        onDelete: () -> Unit,
        onEdit: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            MoneyTheme {
                SwipeRevealActionsBox(
                    endAction = SwipeRevealAction(
                        label = "删除",
                        icon = Icons.Rounded.Delete,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    startAction = onEdit?.let {
                        SwipeRevealAction(
                            label = "编辑",
                            icon = Icons.Rounded.Edit,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onEndAction = onDelete,
                    onStartAction = onEdit ?: {},
                    modifier = Modifier
                        .height(72.dp)
                        .testTag(SWIPE_TAG),
                ) { contentClick ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clickable(onClick = contentClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("账单")
                    }
                }
            }
        }
    }

    private companion object {
        const val SWIPE_TAG = "swipe-row"
    }
}
