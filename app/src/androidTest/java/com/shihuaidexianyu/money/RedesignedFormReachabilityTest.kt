package com.shihuaidexianyu.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RedesignedFormReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryActionStaysVisibleWhileLongFormScrollsAtLargeText() {
        var saves = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    Box(Modifier.size(width = 360.dp, height = 480.dp)) {
                        MoneyFormPage(
                            title = "记一笔",
                            onBack = {},
                            footer = {
                                MoneySaveButton(onClick = { saves += 1 }, isSaving = false)
                            },
                        ) {
                            items(20) { index -> Text("字段 $index") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("保存").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("字段 19"))
        composeRule.onNodeWithText("字段 19").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }
}
