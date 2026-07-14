package com.shihuaidexianyu.money

import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.home.HomeHeaderActions
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

class AccessibilitySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readonlyRowHasNoButtonOrClickSemantics() {
        composeRule.setContent {
            MoneyTheme {
                MoneyListRow(
                    title = "只读信息",
                    trailing = "历史",
                    showChevron = false,
                )
            }
        }

        composeRule.onNodeWithText("只读信息")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun clickableRowAndSwitchRowExposeOneCoherentAction() {
        composeRule.setContent {
            MoneyTheme {
                MoneyListRow(
                    title = "可点击信息",
                    showChevron = false,
                    isClickable = true,
                    modifier = Modifier.clickable {},
                )
                MoneyListRow(
                    title = "隐藏金额",
                    showChevron = false,
                    accessory = {
                        Switch(checked = true, onCheckedChange = {})
                    },
                )
            }
        }

        composeRule.onNodeWithText("可点击信息").assertHasClickAction()
        composeRule.onNodeWithText("隐藏金额").assertHasClickAction()
    }

    @Test
    fun amountFieldUsesCustomKeypadAndHeaderTargetsMeetMinimumSize() {
        composeRule.setContent {
            MoneyTheme {
                MoneyAmountField(value = "12.34", onValueChange = {})
                HomeHeaderActions(dueCount = 0, onOpenSettings = {}, onOpenReminders = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("金额")
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("设置")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("提醒")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
