package com.shihuaidexianyu.money

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
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
    fun compactRendersBottomBarWithExactlyThreeDestinations() {
        render(AdaptiveNavigationType.BOTTOM_BAR)

        composeRule.onNodeWithTag("top_level_bottom_bar").assertExists()
        assertExactlyThreeDestinations()
    }

    @Test
    fun mediumAndExpandedRenderRailWithExactlyThreeDestinations() {
        render(AdaptiveNavigationType.NAVIGATION_RAIL)

        composeRule.onNodeWithTag("top_level_navigation_rail").assertExists()
        assertExactlyThreeDestinations()
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

    private fun assertExactlyThreeDestinations() {
        check(MoneyDestination.topLevel.size == 3)
        MoneyDestination.topLevel.forEach { destination ->
            val label = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(destination.labelRes)
            composeRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
        }
    }
}
