package com.shihuaidexianyu.money.navigation

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

enum class AdaptiveNavigationType {
    BOTTOM_BAR,
    NAVIGATION_RAIL,
}

fun adaptiveNavigationType(windowWidthDp: Int): AdaptiveNavigationType {
    return if (windowWidthDp < 600) {
        AdaptiveNavigationType.BOTTOM_BAR
    } else {
        AdaptiveNavigationType.NAVIGATION_RAIL
    }
}

enum class LedgerFabAction {
    INCOME,
    EXPENSE,
    TRANSFER,
}

sealed interface LedgerFabDecision {
    data object CreateFirstAccount : LedgerFabDecision

    data class OpenCashForm(
        val direction: CashFlowDirection,
    ) : LedgerFabDecision

    data object NeedSecondAccount : LedgerFabDecision

    data object OpenTransferForm : LedgerFabDecision
}

sealed interface OpenAccountAvailability {
    data object Loading : OpenAccountAvailability

    data class Data(
        val openAccountCount: Int,
    ) : OpenAccountAvailability

    data object Error : OpenAccountAvailability
}

fun openAccountAvailability(
    accounts: Flow<List<Account>>,
): Flow<OpenAccountAvailability> {
    return accounts
        .map<List<Account>, OpenAccountAvailability> { OpenAccountAvailability.Data(it.size) }
        .onStart { emit(OpenAccountAvailability.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(OpenAccountAvailability.Error)
        }
}

fun shouldRenderLedgerFab(availability: OpenAccountAvailability): Boolean {
    return availability is OpenAccountAvailability.Data
}

fun resolveLedgerFabAction(
    action: LedgerFabAction,
    availability: OpenAccountAvailability.Data,
): LedgerFabDecision {
    val openAccountCount = availability.openAccountCount
    if (openAccountCount <= 0) return LedgerFabDecision.CreateFirstAccount
    return when (action) {
        LedgerFabAction.INCOME -> LedgerFabDecision.OpenCashForm(CashFlowDirection.INFLOW)
        LedgerFabAction.EXPENSE -> LedgerFabDecision.OpenCashForm(CashFlowDirection.OUTFLOW)
        LedgerFabAction.TRANSFER -> if (openAccountCount < 2) {
            LedgerFabDecision.NeedSecondAccount
        } else {
            LedgerFabDecision.OpenTransferForm
        }
    }
}

/**
 * Small serializable-friendly model used by shell tests and saved-state adapters.
 * Navigation Compose remains the owner of the actual back stacks.
 */
class TopLevelBackStackState(
    savedRoutes: Map<String, String> = emptyMap(),
    selectedRoute: String = MoneyDestination.Home.route,
) {
    private val lastRoutes = savedRoutes.toMutableMap().apply {
        MoneyDestination.topLevel.forEach { destination ->
            putIfAbsent(destination.route, destination.route)
        }
    }

    var selectedRoute: String = selectedRoute
        private set

    fun updateCurrentRoute(destination: MoneyDestination, route: String) {
        require(destination in MoneyDestination.topLevel)
        lastRoutes[destination.route] = route
    }

    fun select(destination: MoneyDestination): String {
        require(destination in MoneyDestination.topLevel)
        selectedRoute = destination.route
        return lastRoutes.getValue(destination.route)
    }

    fun savedRoutes(): Map<String, String> = lastRoutes.toMap()
}
