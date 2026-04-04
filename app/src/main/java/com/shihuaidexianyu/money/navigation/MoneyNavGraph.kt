package com.shihuaidexianyu.money.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.common.MoneyGradientBackground

private const val NAV_ANIM_DURATION = 300

@Composable
fun MoneyNavGraph(container: MoneyAppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = MoneyDestination.topLevel.map { it.route }.toSet()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            MoneyDestination.topLevel.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        MoneyGradientBackground {
            NavHost(
                navController = navController,
                startDestination = MoneyDestination.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    if (isTopLevelSwitch()) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NAV_ANIM_DURATION))
                    }
                },
                exitTransition = {
                    if (isTopLevelSwitch()) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NAV_ANIM_DURATION))
                    }
                },
                popEnterTransition = {
                    if (isTopLevelSwitch() || isPopToTopLevelDestination()) {
                        EnterTransition.None
                    } else {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NAV_ANIM_DURATION))
                    }
                },
                popExitTransition = {
                    if (isTopLevelSwitch() || isPopToTopLevelDestination()) {
                        ExitTransition.None
                    } else {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NAV_ANIM_DURATION))
                    }
                },
            ) {
                addTopLevelGraph(navController = navController, container = container)
                addAccountsGraph(navController = navController, container = container)
                addRecordGraph(navController = navController, container = container)
                addBalanceGraph(navController = navController, container = container)
            }
        }
    }
}

private fun AnimatedContentTransitionScope<*>.isTopLevelSwitch(): Boolean {
    val topLevel = MoneyDestination.topLevel.map { it.route }.toSet()
    val initial = (initialState as? androidx.navigation.NavBackStackEntry)?.destination?.route
    val target = (targetState as? androidx.navigation.NavBackStackEntry)?.destination?.route
    return initial in topLevel && target in topLevel
}

private fun AnimatedContentTransitionScope<*>.isPopToTopLevelDestination(): Boolean {
    val topLevel = MoneyDestination.topLevel.map { it.route }.toSet()
    val initial = (initialState as? androidx.navigation.NavBackStackEntry)?.destination?.route
    val target = (targetState as? androidx.navigation.NavBackStackEntry)?.destination?.route
    return initial !in topLevel && target in topLevel
}
