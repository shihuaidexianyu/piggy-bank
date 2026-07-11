package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.shouldApplyHistoryLoadResult
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class HistoryLoadGenerationTest {
    @Test
    fun `only a non-cancelled result from the current generation may mutate state`() {
        assertTrue(shouldApplyHistoryLoadResult(requestGeneration = 2, currentGeneration = 2, cancelled = false))
        assertFalse(shouldApplyHistoryLoadResult(requestGeneration = 1, currentGeneration = 2, cancelled = false))
        assertFalse(shouldApplyHistoryLoadResult(requestGeneration = 2, currentGeneration = 2, cancelled = true))
    }
}
