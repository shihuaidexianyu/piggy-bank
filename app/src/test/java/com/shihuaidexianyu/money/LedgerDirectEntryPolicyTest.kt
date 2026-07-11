package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.ui.record.defaultCashAccountId
import com.shihuaidexianyu.money.ui.record.defaultTransferAccountIds
import com.shihuaidexianyu.money.ui.record.normalizeLedgerNote
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class LedgerDirectEntryPolicyTest {
    private val visible = Account(id = 1, name = "现金", initialBalance = 0, createdAt = 1)
    private val hidden = Account(
        id = 2,
        name = "隐藏卡",
        initialBalance = 0,
        createdAt = 1,
        isHidden = true,
    )
    private val closed = Account(
        id = 3,
        name = "已关闭",
        initialBalance = 0,
        createdAt = 1,
        closedAt = 2,
    )

    @Test
    fun `explicit open account wins then recent hidden open then deterministic first`() {
        val accounts = listOf(visible, hidden, closed)

        assertEquals(visible.id, defaultCashAccountId(accounts, listOf(hidden.id), visible.id))
        assertEquals(hidden.id, defaultCashAccountId(accounts, listOf(closed.id, hidden.id), null))
        assertEquals(visible.id, defaultCashAccountId(accounts, emptyList(), null))
        assertEquals(null, defaultCashAccountId(listOf(closed), listOf(closed.id), null))
    }

    @Test
    fun `transfer defaults to recent open source and a distinct open destination`() {
        val selection = defaultTransferAccountIds(
            accounts = listOf(visible, hidden, closed),
            recentAccountIds = listOf(hidden.id, closed.id),
            explicitFromAccountId = null,
        )

        assertEquals(hidden.id, selection.fromAccountId)
        assertEquals(visible.id, selection.toAccountId)
    }

    @Test
    fun `note is optional normalized and limited to 200 characters`() {
        assertEquals("", normalizeLedgerNote("   "))
        assertEquals("午餐", normalizeLedgerNote("  午餐  "))
        assertEquals("a".repeat(200), normalizeLedgerNote("a".repeat(200)))
        assertFailsWith<IllegalArgumentException> {
            normalizeLedgerNote("a".repeat(201))
        }
    }
}
