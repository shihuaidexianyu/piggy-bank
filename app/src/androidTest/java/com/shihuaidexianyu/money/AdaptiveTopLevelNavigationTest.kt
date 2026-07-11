package com.shihuaidexianyu.money

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.shihuaidexianyu.money.navigation.AdaptiveNavigationType
import com.shihuaidexianyu.money.navigation.AdaptiveTopLevelNavigation
import com.shihuaidexianyu.money.navigation.MoneyDestination
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import org.junit.Rule
import org.junit.Test

class AdaptiveTopLevelNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactRendersBottomBarWithExactlyFourDestinations() {
        render(AdaptiveNavigationType.BOTTOM_BAR)

        composeRule.onNodeWithTag("top_level_bottom_bar").assertExists()
        assertExactlyFourDestinations()
    }

    @Test
    fun mediumAndExpandedRenderRailWithExactlyFourDestinations() {
        render(AdaptiveNavigationType.NAVIGATION_RAIL)

        composeRule.onNodeWithTag("top_level_navigation_rail").assertExists()
        assertExactlyFourDestinations()
    }

    private fun render(type: AdaptiveNavigationType) {
        composeRule.setContent {
            MoneyTheme {
                AdaptiveTopLevelNavigation(
                    type = type,
                    currentRoute = MoneyDestination.Home.route,
                    onDestinationClick = {},
                )
            }
        }
    }

    private fun assertExactlyFourDestinations() {
        check(MoneyDestination.topLevel.size == 4)
        MoneyDestination.topLevel.forEach { destination ->
            composeRule.onNodeWithContentDescription(destination.label).assertExists()
        }
    }
}
