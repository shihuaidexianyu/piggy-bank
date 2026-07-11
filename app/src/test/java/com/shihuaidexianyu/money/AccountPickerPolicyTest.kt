package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.accountPickerSections
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountPickerPolicyTest {
    @Test
    fun `mapper retains hidden state for every account option shape`() {
        val hidden = Account(
            id = 7L,
            name = "备用金",
            initialBalance = 0L,
            createdAt = 1L,
            isHidden = true,
        )

        assertTrue(hidden.toAccountOptionUiModel().isHidden)
        assertTrue(hidden.toAccountOptionUiModel(balance = 100L).isHidden)
        assertTrue(hidden.toAccountOptionUiModel(balance = 100L, isStale = true).isHidden)
    }

    @Test
    fun `hidden accounts stay collapsed until explicitly expanded`() {
        val normal = AccountOptionUiModel(id = 1L, name = "现金")
        val hidden = AccountOptionUiModel(id = 2L, name = "备用金", isHidden = true)

        val collapsed = accountPickerSections(listOf(normal, hidden), hiddenExpanded = false)
        assertEquals(listOf(normal), collapsed.visibleAccounts)
        assertTrue(collapsed.hiddenAccounts.isEmpty())
        assertEquals(1, collapsed.hiddenAccountCount)
        assertFalse(collapsed.hiddenExpanded)

        val expanded = accountPickerSections(listOf(normal, hidden), hiddenExpanded = true)
        assertEquals(listOf(normal), expanded.visibleAccounts)
        assertEquals(listOf(hidden), expanded.hiddenAccounts)
        assertTrue(expanded.hiddenExpanded)
    }
}
