package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HISTORY_HEADER_ITEM_COUNT
import com.shihuaidexianyu.money.ui.history.historyAnchorScrollIndex
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class HistoryScrollAnchorsTest {
    @Test
    fun `anchor in a day with multiple records targets that date header, not the record offset`() {
        // The preceding group contributes one date header and three separate record rows.
        val labels = listOf("2024-04-03", "2024-04-03", "2024-04-03", "2024-04-02", "2024-04-02")

        assertEquals(HISTORY_HEADER_ITEM_COUNT + 4, historyAnchorScrollIndex("2024-04-02", labels))
    }

    @Test
    fun `anchor in the first group targets the item right after the header`() {
        val labels = listOf("2024-04-03", "2024-04-03", "2024-04-02")

        assertEquals(HISTORY_HEADER_ITEM_COUNT, historyAnchorScrollIndex("2024-04-03", labels))
    }

    @Test
    fun `anchor in the last group targets the last date header`() {
        val labels = listOf("2024-04-03", "2024-04-02", "2024-04-01", "2024-04-01")

        assertEquals(HISTORY_HEADER_ITEM_COUNT + 4, historyAnchorScrollIndex("2024-04-01", labels))
    }

    @Test
    fun `missing or absent anchor yields no scroll target`() {
        val labels = listOf("2024-04-03", "2024-04-02")

        assertNull(historyAnchorScrollIndex("2024-04-01", labels))
        assertNull(historyAnchorScrollIndex(null, labels))
        assertNull(historyAnchorScrollIndex("2024-04-03", emptyList()))
    }
}
