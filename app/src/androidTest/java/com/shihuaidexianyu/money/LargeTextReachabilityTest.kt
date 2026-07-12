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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

class LargeTextReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeBottomContentRemainsReachableAtTwoHundredPercentTextOnShortPhone() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        HomeScreen(
                            state = HomeUiState(
                                isLoading = false,
                                hasCommittedContent = true,
                                hasAnyAccounts = true,
                                allAccountCount = 1,
                                totalAssets = 123_456_789L,
                                periodCashInflow = 20_000L,
                                periodCashOutflow = 10_000L,
                            ),
                            onStartUpdateBalance = {},
                            onAllRemindersClick = {},
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("待核对账户").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun formBottomActionRemainsReachableAtTwoHundredPercentTextInLandscape() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MoneyTheme {
                    Box(Modifier.size(width = 800.dp, height = 360.dp)) {
                        MoneyFormPage(title = "大字号表单") {
                            items(12) { index -> Text("字段 $index") }
                            item { Text("保存表单") }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("保存表单").performScrollTo().assertIsDisplayed()
    }
}
