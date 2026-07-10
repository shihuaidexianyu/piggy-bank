package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.historyEndDateFieldText
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

class HistoryDateDisplayTest {
    @Test
    fun `end date field displays the inclusive calendar date`() {
        val endExclusive = Instant.parse("2024-04-04T00:00:00Z").toEpochMilli()

        assertEquals(
            "2024-04-03",
            historyEndDateFieldText(endExclusive, ZoneOffset.UTC),
        )
    }

    @Test
    fun `end date field displays unlimited when unset`() {
        assertEquals("不限", historyEndDateFieldText(null, ZoneOffset.UTC))
    }
}
