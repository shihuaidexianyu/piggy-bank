package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.stats.calculateAssetFlowLayout
import kotlin.test.assertTrue
import org.junit.Test

class AssetFlowLayoutTest {
    @Test
    fun `middle row nodes fit within narrow phone card width`() {
        val layout = calculateAssetFlowLayout(widthDp = 280f)

        val middleRowWidth = layout.nodeWidth.value * 3f + layout.middleGap.value * 2f
        assertTrue(middleRowWidth <= 280f)
        assertTrue(layout.bottomNodeWidth.value <= 280f)
        assertTrue(layout.nodeHeight.value in 48f..56f)
    }

    @Test
    fun `node widths scale with wider card width`() {
        val narrow = calculateAssetFlowLayout(widthDp = 280f)
        val wide = calculateAssetFlowLayout(widthDp = 520f)

        assertTrue(wide.nodeWidth.value > narrow.nodeWidth.value)
        assertTrue(wide.diagramHeight.value >= narrow.diagramHeight.value)
    }

    @Test
    fun `layout dimensions are density independent`() {
        val phoneCardWidthDp = 320f
        val density = 420f / 160f
        val layout = calculateAssetFlowLayout(widthDp = phoneCardWidthDp)

        assertTrue(layout.nodeHeight.value >= 48f)
        assertTrue(layout.diagramHeight.value >= 184f)
        assertTrue(layout.diagramHeight.value * density >= 480f)
    }
}
