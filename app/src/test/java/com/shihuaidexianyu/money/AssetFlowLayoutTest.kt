package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.stats.calculateAssetFlowLayout
import kotlin.test.assertTrue
import org.junit.Test

class AssetFlowLayoutTest {
    @Test
    fun `middle row nodes fit within narrow phone card width`() {
        val layout = calculateAssetFlowLayout(width = 280f)

        val middleRowWidth = layout.middleNodeWidth * 3f + layout.middleGap * 2f

        assertTrue(middleRowWidth <= 280f)
        assertTrue(layout.bottomNodeWidth <= 280f)
        assertTrue(layout.nodeHeight in 48f..56f)
    }

    @Test
    fun `node widths scale with wider card width`() {
        val narrow = calculateAssetFlowLayout(width = 280f)
        val wide = calculateAssetFlowLayout(width = 520f)

        assertTrue(wide.middleNodeWidth > narrow.middleNodeWidth)
        assertTrue(wide.diagramHeight >= narrow.diagramHeight)
    }
}
