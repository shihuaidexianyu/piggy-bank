package com.shihuaidexianyu.money.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

@Composable
fun AdaptiveTopLevelNavigation(
    type: AdaptiveNavigationType,
    currentRoute: String?,
    onDestinationClick: (MoneyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (type) {
        AdaptiveNavigationType.BOTTOM_BAR -> NavigationBar(
            modifier = modifier.testTag("top_level_bottom_bar"),
        ) {
            MoneyDestination.topLevel.forEach { destination ->
                val label = stringResource(destination.labelRes)
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = { onDestinationClick(destination) },
                    icon = {
                        Icon(destination.icon, contentDescription = label)
                    },
                    label = { Text(label) },
                )
            }
        }

        AdaptiveNavigationType.NAVIGATION_RAIL -> NavigationRail(
            modifier = modifier.testTag("top_level_navigation_rail"),
        ) {
            MoneyDestination.topLevel.forEach { destination ->
                val label = stringResource(destination.labelRes)
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { onDestinationClick(destination) },
                    icon = { Icon(destination.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        }
    }
}
