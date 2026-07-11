package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.widget.WidgetBalanceSnapshot
import com.shihuaidexianyu.money.widget.WidgetRefreshCoordinator
import com.shihuaidexianyu.money.widget.WidgetPrivacyGeneration
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class WidgetRefreshCoordinatorTest {
    @Test
    fun `no widget instances performs no snapshot access`() = runBlocking {
        var snapshotReads = 0
        var renderCalls = 0
        val coordinator = WidgetRefreshCoordinator(
            snapshotSource = {
                snapshotReads++
                snapshot()
            },
            renderer = { _, _ -> renderCalls++ },
        )

        coordinator.refresh(intArrayOf())

        assertEquals(0, snapshotReads)
        assertEquals(0, renderCalls)
    }

    @Test
    fun `one snapshot updates every widget instance`() = runBlocking {
        var snapshotReads = 0
        val rendered = mutableListOf<Pair<Int, WidgetBalanceSnapshot>>()
        val expected = snapshot()
        val coordinator = WidgetRefreshCoordinator(
            snapshotSource = {
                snapshotReads++
                expected
            },
            renderer = { id, snapshot -> rendered += id to snapshot },
        )

        coordinator.refresh(intArrayOf(2, 4, 9))

        assertEquals(1, snapshotReads)
        assertEquals(listOf(2, 4, 9), rendered.map { it.first })
        assertEquals(listOf(expected, expected, expected), rendered.map { it.second })
    }

    @Test
    fun `snapshot failure replaces prior real values with safe placeholders first`() = runBlocking {
        val renderedText = mutableMapOf(1 to "¥12,345.67", 2 to "¥88.00")
        val coordinator = WidgetRefreshCoordinator(
            snapshotSource = { error("privacy read failed") },
            renderer = { _, _ -> error("must not render failed snapshot") },
            safeRenderer = { id -> renderedText[id] = "¥••••" },
        )

        runCatching { coordinator.refresh(intArrayOf(1, 2)) }

        assertEquals(listOf("¥••••", "¥••••"), renderedText.values.toList())
        renderedText.values.forEach { text ->
            assertEquals(false, text.any(Char::isDigit))
        }
    }

    @Test
    fun `privacy generation invalidates visible rendering between widget instances`() {
        val visibleEpoch = WidgetPrivacyGeneration.update(hidden = false)
        var rendered = ""
        WidgetPrivacyGeneration.renderAtomically(
            capturedGeneration = visibleEpoch.generation,
            snapshotIsMasked = false,
            renderSnapshot = { rendered = "visible" },
            renderSafe = { rendered = "safe" },
        )
        assertEquals("visible", rendered)

        WidgetPrivacyGeneration.update(hidden = true)
        WidgetPrivacyGeneration.renderAtomically(
            capturedGeneration = visibleEpoch.generation,
            snapshotIsMasked = false,
            renderSnapshot = { rendered = "visible" },
            renderSafe = { rendered = "safe" },
        )
        assertEquals("safe", rendered)
    }

    private fun snapshot() = WidgetBalanceSnapshot(
        totalAssets = 10L,
        monthInflow = 20L,
        monthOutflow = 5L,
        settings = PortableSettings(),
        visibility = AmountVisibility.VISIBLE,
    )
}
