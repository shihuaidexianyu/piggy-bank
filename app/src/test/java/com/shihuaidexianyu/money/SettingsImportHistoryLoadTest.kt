package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.settings.commitPortableSettingsMutation
import com.shihuaidexianyu.money.ui.settings.rollbackAndRefreshImportHistory
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SettingsImportHistoryLoadTest {
    @Test
    fun `committed portable settings mutation invalidates eligibility but failed write leaves it visible`() = runTest {
        var currentCurrency = "¥"
        var eligibleReceiptId: String? = "receipt-1"
        var refreshCount = 0

        val committed = commitPortableSettingsMutation(
            mutation = { currentCurrency = "$" },
            refreshImportHistory = {
                refreshCount++
                eligibleReceiptId = null
            },
        )

        assertTrue(committed.isSuccess)
        assertEquals("$", currentCurrency)
        assertNull(eligibleReceiptId)
        assertEquals(1, refreshCount)

        eligibleReceiptId = "receipt-2"
        val failed = commitPortableSettingsMutation(
            mutation = { error("Room write failed") },
            refreshImportHistory = {
                refreshCount++
                eligibleReceiptId = null
            },
        )

        assertTrue(failed.isFailure)
        assertEquals("receipt-2", eligibleReceiptId)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `rollback CAS failure still refreshes stale eligibility`() = runTest {
        var eligibleReceiptId: String? = "receipt-1"

        val result = rollbackAndRefreshImportHistory(
            rollback = { error("导入后账本已发生变化，不能直接撤销") },
            refreshImportHistory = { eligibleReceiptId = null },
        )

        assertTrue(result.isFailure)
        assertNull(eligibleReceiptId)
    }

}
