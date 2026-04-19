package com.shihuaidexianyu.money.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.common.MoneyGradientBackground

private val topLevelRoutes = MoneyDestination.topLevel.map { it.route }
private val topLevelRouteSet = topLevelRoutes.toSet()

private fun isTopLevelTransition(initial: String?, target: String?): Boolean {
    return initial in topLevelRouteSet && target in topLevelRouteSet
}

private fun topLevelDirection(initial: String, target: String): AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = topLevelRoutes.indexOf(initial)
    val targetIndex = topLevelRoutes.indexOf(target)
    return if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

@Composable
fun MoneyNavGraph(container: MoneyAppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
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
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        slideIntoContainer(
                            topLevelDirection(initial!!, target!!),
                            animationSpec = tween(250),
                        )
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(250),
                        )
                    }
                },
                exitTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        slideOutOfContainer(
                            topLevelDirection(initial!!, target!!),
                            animationSpec = tween(250),
                        )
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(250),
                        )
                    }
                },
                popEnterTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        slideIntoContainer(
                            topLevelDirection(target!!, initial!!),
                            animationSpec = tween(250),
                        )
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(250),
                        )
                    }
                },
                popExitTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        slideOutOfContainer(
                            topLevelDirection(target!!, initial!!),
                            animationSpec = tween(250),
                        )
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(250),
                        )
                    }
                },
            ) {
                addTopLevelGraph(navController = navController, container = container)
                addAccountsGraph(navController = navController, container = container)
                addRecordGraph(navController = navController, container = container)
                addBalanceGraph(navController = navController, container = container)
                addReminderGraph(navController = navController, container = container)
            }
        }
    }
}
