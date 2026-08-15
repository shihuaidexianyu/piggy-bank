package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.accounts.AccountListItemUiModel
import com.shihuaidexianyu.money.ui.accounts.accountGroups
import com.shihuaidexianyu.money.ui.accounts.accountClosurePresentation
import com.shihuaidexianyu.money.ui.accounts.AccountDetailUiState
import com.shihuaidexianyu.money.ui.accounts.canMutateLedger
import com.shihuaidexianyu.money.ui.home.netWorthGoalProgressPresentation
import com.shihuaidexianyu.money.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountsScreenProjectionTest {
    @Test
    fun `net worth goal percentage is exact and geometry clamps without long overflow`() {
        val negative = netWorthGoalProgressPresentation(Long.MIN_VALUE, targetAmount = 1L)
        assertEquals(0, negative.geometryPercent)
        assertEquals("-922337203685477580800%", negative.percentageText)
        assertEquals(Long.MAX_VALUE, negative.remainingAmount)

        val huge = netWorthGoalProgressPresentation(Long.MAX_VALUE, targetAmount = 1L)
        assertEquals(100, huge.geometryPercent)
        assertEquals("922337203685477580700%", huge.percentageText)
        assertEquals(0L, huge.remainingAmount)

        val partial = netWorthGoalProgressPresentation(currentAmount = 1L, targetAmount = 3L)
        assertEquals(33, partial.geometryPercent)
        assertEquals("33%", partial.percentageText)
        assertEquals(2L, partial.remainingAmount)
    }

    @Test
    fun `account groups distinguish normal hidden and closed without dropping balances`() {
        val normal = item(id = 1L, balance = 100L)
        val hidden = item(id = 2L, balance = 200L, isHidden = true)
        val closed = item(id = 3L, balance = 300L, isClosed = true)

        val groups = accountGroups(
            openAccounts = listOf(hidden, normal),
            closedAccounts = listOf(closed),
        )

        assertEquals(listOf(normal), groups.normal)
        assertEquals(listOf(hidden), groups.hidden)
        assertEquals(listOf(closed), groups.closed)
        assertEquals(600L, groups.all.map(AccountListItemUiModel::balance).sum())
    }

    @Test
    fun `closed account presentation distinguishes migrated nonzero balance and open mutation`() {
        val migrated = accountClosurePresentation(isClosed = true, balance = -50L)
        assertFalse(migrated.canMutate)
        assertTrue(migrated.canReopen)
        assertEquals(R.string.account_status_reopen_settle, migrated.statusTextRes)

        val regularClosed = accountClosurePresentation(isClosed = true, balance = 0L)
        assertEquals(R.string.account_status_closed, regularClosed.statusTextRes)

        assertTrue(AccountDetailUiState(isLoading = false, isClosed = false).canMutateLedger())
        assertFalse(AccountDetailUiState(isLoading = false, isClosed = true).canMutateLedger())
    }

    private fun item(
        id: Long,
        balance: Long,
        isHidden: Boolean = false,
        isClosed: Boolean = false,
    ) = AccountListItemUiModel(
        id = id,
        name = "账户$id",
        colorName = "blue",
        iconName = "wallet",
        balance = balance,
        isHidden = isHidden,
        isClosed = isClosed,
        isStale = false,
        displayOrder = id.toInt(),
    )
}
