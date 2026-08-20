package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.accounts.AccountListItemUiModel
import com.shihuaidexianyu.money.ui.accounts.accountGroups
import com.shihuaidexianyu.money.ui.accounts.accountClosurePresentation
import com.shihuaidexianyu.money.ui.accounts.AccountDetailUiState
import com.shihuaidexianyu.money.ui.accounts.canMutateLedger
import com.shihuaidexianyu.money.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountsScreenProjectionTest {
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
