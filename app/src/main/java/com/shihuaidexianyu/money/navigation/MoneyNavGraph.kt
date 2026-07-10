package com.shihuaidexianyu.money.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.common.MoneyGradientBackground
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchDestination
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchRequest
import kotlinx.coroutines.CancellationException

private val topLevelRoutes = MoneyDestination.topLevel.map { it.route }
private val topLevelRouteSet = topLevelRoutes.toSet()

private fun isTopLevelTransition(initial: String?, target: String?): Boolean {
    return initial in topLevelRouteSet && target in topLevelRouteSet
}

// === Top-level tab transitions: soft fade + scale ===
private fun topLevelEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(280)) + scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(280),
    )
}

private fun topLevelExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(200)) + scaleOut(
        targetScale = 1.02f,
        animationSpec = tween(200),
    )
}

// === Sub-page enter: slide in from right + fade ===
private fun subPageEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))
}

// === Sub-page exit (forward): slide out to left + fade + slight scale ===
private fun subPageExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(200)) + scaleOut(
        targetScale = 0.98f,
        animationSpec = tween(300),
    )
}

// === Pop enter: slide in from left + fade ===
private fun popEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300)) + scaleIn(
        initialScale = 1.02f,
        animationSpec = tween(300),
    )
}

// === Pop exit: slide out to right + fade ===
private fun popExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(250))
}

@Composable
fun MoneyNavGraph(
    container: MoneyAppContainer,
    shortcutAction: String? = null,
    sharedAmount: Long? = null,
    notificationLaunchRequest: NotificationLaunchRequest? = null,
    onNotificationLaunchConsumed: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
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
                    note = null,
                    reminderId = null,
                    expectedDueAt = null,
                ),
            )
        }
    }

    LaunchedEffect(notificationLaunchRequest?.token) {
        val request = notificationLaunchRequest ?: return@LaunchedEffect
        val destination = try {
            container.resolveNotificationLaunchUseCase(request.identity)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            NotificationLaunchDestination.ReminderCenter(stateChanged = true)
        }
        when (destination) {
            is NotificationLaunchDestination.ProcessReminder -> {
                val reminder = destination.reminder
                navController.navigate(
                    MoneyDestination.recordCashFlowRoute(
                        direction = CashFlowDirection.fromValue(reminder.direction),
                        accountId = reminder.accountId,
                        amount = reminder.amount,
                        note = reminder.name,
                        reminderId = reminder.id,
                        expectedDueAt = reminder.nextDueAt,
                    ),
                )
            }
            is NotificationLaunchDestination.ReconcileBalance ->
                navController.navigate(MoneyDestination.updateBalanceRoute(destination.accountId))
            is NotificationLaunchDestination.ReminderCenter -> {
                navController.navigate(MoneyDestination.ReminderListRoute)
                if (destination.stateChanged) {
                    snackbarHostState.showSnackbar("提醒状态已变化")
                }
            }
        }
        onNotificationLaunchConsumed(request.token)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        subPageEnterTransition()
                    }
                },
                exitTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        topLevelExitTransition()
                    } else {
                        subPageExitTransition()
                    }
                },
                popEnterTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        topLevelEnterTransition()
                    } else {
                        popEnterTransition()
                    }
                },
                popExitTransition = {
                    val initial = initialState.destination.route
                    val target = targetState.destination.route
                    if (isTopLevelTransition(initial, target)) {
                        topLevelExitTransition()
                    } else {
                        popExitTransition()
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
