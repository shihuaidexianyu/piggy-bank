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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.RootSnackbarQueueViewModel
import com.shihuaidexianyu.money.ui.common.executeRootSnackbarAction
import com.shihuaidexianyu.money.ui.common.RootActionExecutionResult
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.rootSnackbarDuration
import com.shihuaidexianyu.money.ui.lock.AppLockFeedback
import com.shihuaidexianyu.money.ui.theme.LocalDarkTheme
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchDestination
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.domain.launch.AppLaunchDestination
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

private suspend fun resolveNotificationDestination(
    container: MoneyAppContainer,
    identity: NotificationLaunchIdentity,
): NotificationLaunchDestination = try {
    container.resolveNotificationLaunchUseCase(identity)
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    NotificationLaunchDestination.ReminderCenter(stateChanged = true)
}

private suspend fun routeNotificationDestination(
    destination: NotificationLaunchDestination,
    navController: androidx.navigation.NavHostController,
): Boolean {
    return when (destination) {
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
            false
        }
        is NotificationLaunchDestination.ReconcileBalance -> {
            navController.navigate(MoneyDestination.updateBalanceRoute(destination.accountId))
            false
        }
        is NotificationLaunchDestination.ReminderCenter -> {
            navController.navigate(MoneyDestination.ReminderListRoute)
            destination.stateChanged
        }
    }
}

@Composable
fun MoneyNavGraph(
    container: MoneyAppContainer,
    appLaunchRequest: AppLaunchRequest? = null,
    onAppLaunchConsumed: (String) -> Unit = {},
    onBiometricLockChange: (Boolean) -> Unit = {},
    appLockFeedback: kotlinx.coroutines.flow.Flow<AppLockFeedback>? = null,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootSnackbarQueue: RootSnackbarQueueViewModel = viewModel(
        factory = moneySavedStateViewModelFactory { RootSnackbarQueueViewModel(it) },
    )
    val rootSnackbarItems by rootSnackbarQueue.queue.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in topLevelRoutes
    val windowWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
    }
    val navigationType = adaptiveNavigationType(windowWidthDp)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var navigationBarHeight by remember { mutableStateOf(81.dp) }
    var navigationRailWidth by remember { mutableStateOf(80.dp) }
    val openAccountAvailabilityFlow = remember(container) {
        openAccountAvailability(container.accountRepository.observeOpenAccounts())
    }
    val openAccountAvailability by openAccountAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = OpenAccountAvailability.Loading,
    )
    var fabExpanded by remember { mutableStateOf(false) }
    var historyScrolled by remember { mutableStateOf(false) }
    val createFirstAccountMessage = stringResource(R.string.ledger_fab_create_first_message)
    val createAccountLabel = stringResource(R.string.accounts_create)
    val needSecondAccountMessage = stringResource(R.string.ledger_fab_need_second_message)
    val manageAccountsLabel = stringResource(R.string.ledger_fab_manage_accounts)
    val notificationStateChangedMessage = stringResource(R.string.notification_state_changed)
    val retryLabel = stringResource(R.string.action_retry)

    fun navigateTopLevel(destination: MoneyDestination) {
        navController.navigateToTopLevelTab(destination)
    }

    fun handleFabAction(action: LedgerFabAction) {
        val availability = openAccountAvailability as? OpenAccountAvailability.Data ?: return
        fabExpanded = false
        when (val decision = resolveLedgerFabAction(action, availability)) {
            LedgerFabDecision.CreateFirstAccount -> rootSnackbarQueue.enqueue(
                message = createFirstAccountMessage,
                actionLabel = createAccountLabel,
                action = RootSnackbarAction.CreateAccount,
            )
            is LedgerFabDecision.OpenCashForm -> navController.navigate(
                MoneyDestination.recordCashFlowRoute(decision.direction, accountId = 0L),
            )
            LedgerFabDecision.NeedSecondAccount -> rootSnackbarQueue.enqueue(
                message = needSecondAccountMessage,
                actionLabel = manageAccountsLabel,
                action = RootSnackbarAction.ManageAccounts,
            )
            LedgerFabDecision.OpenTransferForm -> navController.navigate(
                MoneyDestination.recordTransferRoute(),
            )
            LedgerFabDecision.OpenReconcileForm -> navController.navigate(
                MoneyDestination.updateBalanceRoute(accountId = 0L),
            )
        }
    }

    if (fabExpanded) {
        LedgerActionDialog(
            onDismiss = { fabExpanded = false },
            onAction = ::handleFabAction,
        )
    }

    val appContext = LocalContext.current
    LaunchedEffect(appLockFeedback) {
        appLockFeedback?.collect { feedback ->
            rootSnackbarQueue.enqueue(appContext.getString(feedback.messageRes))
        }
    }

    LaunchedEffect(appLaunchRequest?.token) {
        val request = appLaunchRequest ?: return@LaunchedEffect
        var showNotificationStateChanged = false
        when (val requested = request.destination) {
            AppLaunchDestination.BatchReconcile ->
                navController.navigate(MoneyDestination.BatchReconcileRoute)
            AppLaunchDestination.Transfer ->
                navController.navigate(MoneyDestination.recordTransferRoute())
            is AppLaunchDestination.CashFlow -> navController.navigate(
                MoneyDestination.recordCashFlowRoute(requested.direction, accountId = 0L),
            )
            is AppLaunchDestination.RecurringNotification -> showNotificationStateChanged =
                routeNotificationDestination(
                destination = resolveNotificationDestination(
                    container = container,
                    identity = NotificationLaunchIdentity.Recurring(
                        requested.reminderId,
                        requested.expectedDueAt,
                    ),
                ),
                navController = navController,
            )
            is AppLaunchDestination.BalanceNotification -> showNotificationStateChanged =
                routeNotificationDestination(
                destination = resolveNotificationDestination(
                    container = container,
                    identity = NotificationLaunchIdentity.Balance(requested.accountId),
                ),
                navController = navController,
            )
        }
        onAppLaunchConsumed(request.token)
        if (showNotificationStateChanged) {
            rootSnackbarQueue.enqueue(notificationStateChangedMessage)
        }
    }

    LaunchedEffect(rootSnackbarItems.firstOrNull()?.token) {
        val effect = rootSnackbarItems.firstOrNull() ?: return@LaunchedEffect
        // Actionable messages stay visible for Material's long, accessibility-aware timeout.
        // Non-actionable status messages keep the shorter duration so the FIFO queue can advance.
        val result = snackbarHostState.showSnackbar(
            message = effect.message,
            actionLabel = effect.actionLabel,
            duration = rootSnackbarDuration(effect),
        )
        if (result == SnackbarResult.ActionPerformed) {
            when (val execution = executeRootSnackbarAction(
                action = effect.action,
                restoreLedger = container.restoreLedgerRecordUseCase::invoke,
                undoReminderSkip = container.undoSkipReminderUseCase::invoke,
                createAccount = {
                    navController.navigate(MoneyDestination.CreateAccountRoute) { launchSingleTop = true }
                },
                manageAccounts = { navigateTopLevel(MoneyDestination.Accounts) },
                unhideAccount = { accountId ->
                    container.setAccountHiddenUseCase(accountId, hidden = false)
                },
            )) {
                RootActionExecutionResult.Success -> rootSnackbarQueue.ack(effect.token)
                is RootActionExecutionResult.PermanentFailure -> rootSnackbarQueue.replaceHead(
                    effect.token,
                    rootSnackbarEffect(execution.message),
                )
                is RootActionExecutionResult.RetryableFailure -> rootSnackbarQueue.replaceHead(
                    effect.token,
                    rootSnackbarEffect(
                        message = execution.message,
                        actionLabel = retryLabel,
                        action = effect.action,
                    ),
                )
            }
            return@LaunchedEffect
        }
        rootSnackbarQueue.ack(effect.token)
    }

    CompositionLocalProvider(
        LocalRootSnackbarDispatcher provides RootSnackbarDispatcher { effect ->
            rootSnackbarQueue.enqueue(effect)
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Every screen hosts its own top app bar, which consumes the status bar inset itself;
            // this Scaffold has no topBar, so reserving the top inset here too would double-pad
            // the header with dead space. Keep only the bottom/horizontal insets for content.
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
            ),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (isTopLevel && shouldRenderLedgerFab(openAccountAvailability)) {
                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = true },
                        expanded = currentRoute != MoneyDestination.History.route || !historyScrolled,
                        icon = {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = if (currentRoute == MoneyDestination.History.route && historyScrolled) {
                                    stringResource(R.string.ledger_fab_title)
                                } else null,
                            )
                        },
                        text = { Text(stringResource(R.string.ledger_fab_title)) },
                        // Full-round capsule in primary roles, matching the filled primary button used
                        // inside dialogs.
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            bottomBar = {
                if (isTopLevel && navigationType == AdaptiveNavigationType.BOTTOM_BAR) {
                    AdaptiveTopLevelNavigation(
                        type = navigationType,
                        currentRoute = currentRoute,
                        onDestinationClick = ::navigateTopLevel,
                        modifier = Modifier.onSizeChanged {
                            navigationBarHeight = with(density) { it.height.toDp() } - systemBottomPadding
                        },
                    )
                }
            },
        ) { innerPadding ->
            val systemContentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                bottom = systemBottomPadding,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(systemContentPadding)
                    .consumeWindowInsets(systemContentPadding),
            ) {
                CompositionLocalProvider(
                    LocalTopLevelContentPadding provides PaddingValues(
                        start = if (navigationType == AdaptiveNavigationType.NAVIGATION_RAIL) navigationRailWidth else 0.dp,
                        bottom = if (navigationType == AdaptiveNavigationType.BOTTOM_BAR) navigationBarHeight else 0.dp,
                    ),
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = MoneyDestination.Home.route,
                        modifier = Modifier.fillMaxSize(),
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
                            if (initial == MoneyDestination.History.route && target !in topLevelRouteSet) {
                                ExitTransition.None
                            } else if (isTopLevelTransition(initial, target)) {
                                topLevelExitTransition()
                            } else {
                                subPageExitTransition()
                            }
                        },
                        popEnterTransition = {
                            val initial = initialState.destination.route
                            val target = targetState.destination.route
                            if (target == MoneyDestination.History.route && initial !in topLevelRouteSet) {
                                EnterTransition.None
                            } else if (isTopLevelTransition(initial, target)) {
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
                        addTopLevelGraph(
                            navController = navController,
                            container = container,
                            onBiometricLockChange = onBiometricLockChange,
                            onHistoryScrolledChange = { historyScrolled = it },
                        )
                        addAccountsGraph(navController = navController, container = container)
                        addRecordGraph(navController = navController, container = container)
                        addBalanceGraph(navController = navController, container = container)
                        addReminderGraph(navController = navController, container = container)
                    }
                }
                if (isTopLevel && navigationType == AdaptiveNavigationType.NAVIGATION_RAIL) {
                    AdaptiveTopLevelNavigation(
                        type = navigationType,
                        currentRoute = currentRoute,
                        onDestinationClick = ::navigateTopLevel,
                        modifier = Modifier.align(Alignment.TopStart).onSizeChanged {
                            navigationRailWidth = with(density) { it.width.toDp() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LedgerActionDialog(
    onDismiss: () -> Unit,
    onAction: (LedgerFabAction) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.ledger_fab_title), style = MaterialTheme.typography.headlineSmall)
            LedgerActionRow(stringResource(R.string.ledger_expense), Icons.AutoMirrored.Rounded.TrendingDown) {
                onAction(LedgerFabAction.EXPENSE)
            }
            LedgerActionRow(stringResource(R.string.ledger_income), Icons.AutoMirrored.Rounded.TrendingUp) {
                onAction(LedgerFabAction.INCOME)
            }
            LedgerActionRow(stringResource(R.string.history_transfer), Icons.Rounded.SwapHoriz) {
                onAction(LedgerFabAction.TRANSFER)
            }
            LedgerActionRow(stringResource(R.string.ledger_reconcile), Icons.AutoMirrored.Rounded.FactCheck) {
                onAction(LedgerFabAction.RECONCILE)
            }
        }
    }
}

@Composable
private fun LedgerActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    com.shihuaidexianyu.money.ui.common.MoneyListRow(
        title = label,
        onClick = onClick,
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.heightIn(min = 64.dp),
    )
}
