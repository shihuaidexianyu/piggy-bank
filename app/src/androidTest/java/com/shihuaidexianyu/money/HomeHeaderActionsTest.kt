package com.shihuaidexianyu.money

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.ui.home.HomeHeaderActions
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeHeaderActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsIsAvailableFromHomeHeader() {
        var settingsClicks = 0
        composeRule.setContent {
            MoneyTheme {
                HomeHeaderActions(
                    dueCount = 0,
                    onOpenSettings = { settingsClicks += 1 },
                    onOpenReminders = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.runOnIdle { assertEquals(1, settingsClicks) }
    }
}
