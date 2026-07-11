package com.shihuaidexianyu.money

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DirtyFormBackHandlerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dirtyBackRequiresDiscardWhilePristineBackExitsImmediately() {
        var exits = 0
        composeRule.setContent {
            MoneyTheme {
                val requestBack = rememberDirtyFormBackAction(
                    isDirty = true,
                    onExit = { exits += 1 },
                )
                Button(onClick = requestBack) { Text("返回") }
            }
        }

        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertEquals(0, exits) }
        composeRule.onNodeWithText("放弃未保存的更改？").assertExists()
        composeRule.onNodeWithText("放弃").performClick()
        composeRule.runOnIdle { assertEquals(1, exits) }
    }
}
