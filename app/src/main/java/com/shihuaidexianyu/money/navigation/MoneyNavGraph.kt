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
    val openAccountAvailabilityFlow = remember(container) {
        openAccountAvailability(container.accountRepository.observeOpenAccounts())
    }
    val openAccountAvailability by openAccountAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = OpenAccountAvailability.Loading,
    )
    var fabExpanded by remember { mutableStateOf(false) }
    val createFirstAccountMessage = stringResource(R.string.ledger_fab_create_first_message)
    val createAccountLabel = stringResource(R.string.share_create_account)
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
            AppLaunchDestination.Home -> navController.navigateToTopLevelTab(MoneyDestination.Home)
            AppLaunchDestination.BatchReconcile ->
                navController.navigate(MoneyDestination.BatchReconcileRoute)
            AppLaunchDestination.Transfer ->
                navController.navigate(MoneyDestination.recordTransferRoute())
            is AppLaunchDestination.CashFlow -> navController.navigate(
                MoneyDestination.recordCashFlowRoute(requested.direction, accountId = 0L),
            )
            is AppLaunchDestination.SharePreview -> {
                navController.currentBackStackEntry?.savedStateHandle?.set(
                    "shared_text_preview",
                    requested.originalText,
                )
                navController.navigate(MoneyDestination.SharePreviewRoute)
            }
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
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.ledger_fab_title)) },
                    // Standard M3 roles: deep teal + white in light; light mint + dark ink in
                    // dark — matching the filled primary button used inside dark dialogs.
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
                )
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isTopLevel && navigationType == AdaptiveNavigationType.NAVIGATION_RAIL) {
                AdaptiveTopLevelNavigation(
                    type = navigationType,
                    currentRoute = currentRoute,
                    onDestinationClick = ::navigateTopLevel,
                )
            }
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
                    addTopLevelGraph(
                        navController = navController,
                        container = container,
                        onBiometricLockChange = onBiometricLockChange,
                    )
                    addAccountsGraph(navController = navController, container = container)
                    addRecordGraph(navController = navController, container = container)
                    addBalanceGraph(navController = navController, container = container)
                    addReminderGraph(navController = navController, container = container)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        // Icon direction semantics mirror RecordKindBadge: income trends up, expense down.
        val moneyColors = LocalMoneyColors.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.ledger_fab_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.ledger_action_menu_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LedgerActionButton(
                    label = stringResource(R.string.ledger_income),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    accent = moneyColors.income,
                    onClick = { onAction(LedgerFabAction.INCOME) },
                    modifier = Modifier.weight(1f),
                )
                LedgerActionButton(
                    label = stringResource(R.string.ledger_expense),
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    accent = moneyColors.expense,
                    onClick = { onAction(LedgerFabAction.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LedgerActionButton(
                    label = stringResource(R.string.history_transfer),
                    icon = Icons.Rounded.SwapHoriz,
                    accent = moneyColors.transfer,
                    onClick = { onAction(LedgerFabAction.TRANSFER) },
                    modifier = Modifier.weight(1f),
                )
                LedgerActionButton(
                    label = stringResource(R.string.ledger_reconcile),
                    icon = Icons.AutoMirrored.Rounded.FactCheck,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { onAction(LedgerFabAction.RECONCILE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LedgerActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tinted chip: a low-alpha accent wash replaces the muddy solid tonal block, keeping the
    // full-strength accent icon/label clean on top (a stronger wash in dark mode). The accent
    // palette already ships brighter dark variants, so the accent itself stays legible.
    val containerColor = accent.copy(
        alpha = if (LocalDarkTheme.current) 0.16f else 0.10f,
    )
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = accent,
        ),
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Text(label, style = MaterialTheme.typography.titleMedium, color = accent)
        }
    }
}
