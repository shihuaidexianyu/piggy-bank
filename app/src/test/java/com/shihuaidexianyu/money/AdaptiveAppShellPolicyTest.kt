package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.navigation.AdaptiveNavigationType
import com.shihuaidexianyu.money.navigation.LedgerFabAction
import com.shihuaidexianyu.money.navigation.LedgerFabDecision
import com.shihuaidexianyu.money.navigation.MoneyDestination
import com.shihuaidexianyu.money.navigation.OpenAccountAvailability
import com.shihuaidexianyu.money.navigation.TopLevelBackStackState
import com.shihuaidexianyu.money.navigation.adaptiveNavigationType
import com.shihuaidexianyu.money.navigation.resolveLedgerFabAction
import com.shihuaidexianyu.money.navigation.shouldRenderLedgerFab
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AdaptiveAppShellPolicyTest {
    @Test
    fun `top level exposes exactly home history analysis and accounts`() {
        assertEquals(
            listOf("首页", "明细", "分析", "账户"),
            MoneyDestination.topLevel.map { it.label },
        )
        assertEquals(4, MoneyDestination.topLevel.map { it.route }.distinct().size)
    }

    @Test
    fun `compact uses bottom bar while medium and expanded use rail`() {
        assertEquals(AdaptiveNavigationType.BOTTOM_BAR, adaptiveNavigationType(599))
        assertEquals(AdaptiveNavigationType.NAVIGATION_RAIL, adaptiveNavigationType(600))
        assertEquals(AdaptiveNavigationType.NAVIGATION_RAIL, adaptiveNavigationType(840))
    }

    @Test
    fun `fab offers only income expense and transfer with account guards`() {
        assertEquals(
            listOf(LedgerFabAction.INCOME, LedgerFabAction.EXPENSE, LedgerFabAction.TRANSFER),
            LedgerFabAction.entries,
        )
        assertIs<LedgerFabDecision.CreateFirstAccount>(
            resolveLedgerFabAction(LedgerFabAction.INCOME, OpenAccountAvailability.Data(0)),
        )
        assertIs<LedgerFabDecision.OpenCashForm>(
            resolveLedgerFabAction(LedgerFabAction.EXPENSE, OpenAccountAvailability.Data(1)),
        )
        assertIs<LedgerFabDecision.NeedSecondAccount>(
            resolveLedgerFabAction(LedgerFabAction.TRANSFER, OpenAccountAvailability.Data(1)),
        )
        assertIs<LedgerFabDecision.OpenTransferForm>(
            resolveLedgerFabAction(LedgerFabAction.TRANSFER, OpenAccountAvailability.Data(2)),
        )
    }

    @Test
    fun `fab is absent until account availability has real data`() {
        assertEquals(false, shouldRenderLedgerFab(OpenAccountAvailability.Loading))
        assertEquals(false, shouldRenderLedgerFab(OpenAccountAvailability.Error))
        assertEquals(true, shouldRenderLedgerFab(OpenAccountAvailability.Data(0)))
        assertEquals(true, shouldRenderLedgerFab(OpenAccountAvailability.Data(1)))
    }

    @Test
    fun `each top level destination restores its own last route`() {
        val state = TopLevelBackStackState()

        state.updateCurrentRoute(MoneyDestination.Home, "reminders")
        state.select(MoneyDestination.History)
        state.updateCurrentRoute(MoneyDestination.History, "history/cashflow/8")
        state.select(MoneyDestination.Accounts)
        state.updateCurrentRoute(MoneyDestination.Accounts, "accounts/3")

        assertEquals("reminders", state.select(MoneyDestination.Home))
        assertEquals("history/cashflow/8", state.select(MoneyDestination.History))
        assertEquals("accounts/3", state.select(MoneyDestination.Accounts))
    }
}
