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
    when (type) {
        AdaptiveNavigationType.BOTTOM_BAR -> Surface(
            modifier = modifier.testTag("top_level_bottom_bar"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    MoneyDestination.topLevel.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { onDestinationClick(destination) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {
                                Icon(destination.icon, contentDescription = label)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        AdaptiveNavigationType.NAVIGATION_RAIL -> NavigationRail(
            modifier = modifier.testTag("top_level_navigation_rail"),
            containerColor = MaterialTheme.colorScheme.surface,
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
