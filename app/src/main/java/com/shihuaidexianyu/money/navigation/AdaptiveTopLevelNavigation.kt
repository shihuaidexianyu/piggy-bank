package com.shihuaidexianyu.money.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveTopLevelNavigation(
    type: AdaptiveNavigationType,
    currentRoute: String?,
    onDestinationClick: (MoneyDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared item colors so the bottom bar and the rail render selection identically: a jade
    // indicator capsule with onPrimaryContainer glyphs, sitting on the surfaceContainer strip.
    val selectedItemColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedItemColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer

    when (type) {
        AdaptiveNavigationType.BOTTOM_BAR -> Surface(
            modifier = modifier.testTag("top_level_bottom_bar"),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    MoneyDestination.topLevel.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onDestinationClick(destination) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = selectedItemColor,
                                selectedTextColor = selectedItemColor,
                                indicatorColor = indicatorColor,
                                unselectedIconColor = unselectedItemColor,
                                unselectedTextColor = unselectedItemColor,
                            ),
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = label,
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        AdaptiveNavigationType.NAVIGATION_RAIL -> NavigationRail(
            modifier = modifier.testTag("top_level_navigation_rail"),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            MoneyDestination.topLevel.forEach { destination ->
                val label = stringResource(destination.labelRes)
                val selected = currentRoute == destination.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onDestinationClick(destination) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = selectedItemColor,
                        selectedTextColor = selectedItemColor,
                        indicatorColor = indicatorColor,
                        unselectedIconColor = unselectedItemColor,
                        unselectedTextColor = unselectedItemColor,
                    ),
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = label,
                        )
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}
