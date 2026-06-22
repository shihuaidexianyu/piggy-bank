package com.shihuaidexianyu.money.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

private fun topLevelEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(220)) + scaleIn(
        initialScale = 0.98f,
        animationSpec = tween(220),
    )
}

private fun topLevelExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(180)) + scaleOut(
        targetScale = 1.01f,
        animationSpec = tween(180),
    )
}

@Composable
fun MoneyNavGraph(
    container: MoneyAppContainer,
    shortcutAction: String? = null,
    sharedAmount: Long? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Handle app-shortcut deep links: navigate to the target screen once on first composition.
    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            "record_outflow" -> navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    direction = com.shihuaidexianyu.money.domain.model.CashFlowDirection.OUTFLOW,
                    accountId = 0L,
                ),
            )
            "record_inflow" -> navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    direction = com.shihuaidexianyu.money.domain.model.CashFlowDirection.INFLOW,
                    accountId = 0L,
                ),
            )
            "balance_check" -> navController.navigate(MoneyDestination.BatchReconcileRoute)
        }
    }

    // Handle shared text with an extracted amount: default to recording an outflow with the
    // amount pre-filled (most shared texts are payment confirmations / receipts).
    LaunchedEffect(sharedAmount) {
        if (sharedAmount != null && sharedAmount > 0) {
            navController.navigate(
                MoneyDestination.recordCashFlowRoute(
                    direction = com.shihuaidexianyu.money.domain.model.CashFlowDirection.OUTFLOW,
                    accountId = 0L,
                    amount = sharedAmount,
                    purpose = null,
                    reminderId = null,
                ),
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
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
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
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
                        topLevelEnterTransition()
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
                        topLevelExitTransition()
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
                        topLevelEnterTransition()
                    } else {
                        EnterTransition.None
                    }
                },
                popExitTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        topLevelExitTransition()
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
