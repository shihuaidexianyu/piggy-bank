package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.ImportHistoryWithRollbackEligibility
import com.shihuaidexianyu.money.ui.settings.commitPortableSettingsMutation
import com.shihuaidexianyu.money.ui.settings.loadImportHistoryState
import com.shihuaidexianyu.money.ui.settings.rollbackAndRefreshImportHistory
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `damaged receipt index is visible and retry can recover`() = runTest {
        val failed = loadImportHistoryState {
            error("导入收据索引损坏：提交顺序损坏")
        }

        assertFalse(failed.isLoading)
        assertTrue(failed.errorMessage.orEmpty().contains("导入收据索引损坏"))
        assertTrue(failed.receipts.isEmpty())
        assertNull(failed.rollbackEligibleReceiptId)

        val recovered = loadImportHistoryState {
            ImportHistoryWithRollbackEligibility(
                receipts = emptyList(),
                rollbackEligibleReceiptId = null,
            )
        }

        assertFalse(recovered.isLoading)
        assertNull(recovered.errorMessage)
        assertEquals(emptyList(), recovered.receipts)
    }
}
