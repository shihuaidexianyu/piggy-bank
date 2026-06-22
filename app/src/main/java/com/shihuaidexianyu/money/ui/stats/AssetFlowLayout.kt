package com.shihuaidexianyu.money.ui.stats

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout spec for the asset-flow diagram. All dimensions are computed from [widthDp] with
 * named coefficients instead of inline magic numbers, so the diagram scales predictably.
 *
 * Structure (top → bottom):
 *   Row 1: [现金入账]   [现金出账]          (2 nodes, space-between)
 *              ↓  ↓  ←── branch line
 *   Row 2: [期初资产] [现金净额] [调账/对账]  (3 nodes, equal width with gap)
 *              ↓  ↓  ↓  ←── merge line
 *   Row 3:       [期末资产]                 (1 centered node, wider)
 */
data class AssetFlowLayoutSpec(
    val middleGap: Dp,
    val nodeWidth: Dp,
    val bottomNodeWidth: Dp,
    val nodeHeight: Dp,
    val rowGap: Dp,
    val middleRowTop: Dp,
    val bottomRowTop: Dp,
    val branchY: Dp,
    val mergeY: Dp,
    val diagramHeight: Dp,
)

/** Fraction of [widthDp] used as horizontal gap between middle-row nodes. */
private const val GAP_WIDTH_FRACTION = 0.012f
private const val GAP_WIDTH_MIN = 4f
private const val GAP_WIDTH_MAX = 6f

/** Bottom node is this multiple of the middle node width (wider for emphasis). */
private const val BOTTOM_NODE_WIDTH_RATIO = 1.36f

/** Node height as a fraction of node width, clamped to keep nodes readable. */
private const val NODE_HEIGHT_RATIO = 0.48f
private const val NODE_HEIGHT_MIN = 48f
private const val NODE_HEIGHT_MAX = 56f

/** Vertical gap between rows, as a fraction of node height, clamped. */
private const val ROW_GAP_RATIO = 0.46f
private const val ROW_GAP_MIN = 20f
private const val ROW_GAP_MAX = 26f

fun calculateAssetFlowLayout(widthDp: Float): AssetFlowLayoutSpec {
    val middleGap = (widthDp * GAP_WIDTH_FRACTION).coerceIn(GAP_WIDTH_MIN, GAP_WIDTH_MAX).dp
    val nodeWidth = ((widthDp - middleGap.value * 2f) / 3f).dp
    val bottomNodeWidth = (nodeWidth.value * BOTTOM_NODE_WIDTH_RATIO).dp
    val nodeHeight = (nodeWidth.value * NODE_HEIGHT_RATIO).coerceIn(NODE_HEIGHT_MIN, NODE_HEIGHT_MAX).dp
    val rowGap = (nodeHeight.value * ROW_GAP_RATIO).coerceIn(ROW_GAP_MIN, ROW_GAP_MAX).dp
    val middleRowTop = nodeHeight + rowGap
    val bottomRowTop = middleRowTop + nodeHeight + rowGap
    val branchY = nodeHeight + rowGap / 2f
    val mergeY = middleRowTop + nodeHeight + rowGap / 2f
    val diagramHeight = bottomRowTop + nodeHeight

    return AssetFlowLayoutSpec(
        middleGap = middleGap,
        nodeWidth = nodeWidth,
        bottomNodeWidth = bottomNodeWidth,
        nodeHeight = nodeHeight,
        rowGap = rowGap,
        middleRowTop = middleRowTop,
        bottomRowTop = bottomRowTop,
        branchY = branchY,
        mergeY = mergeY,
        diagramHeight = diagramHeight,
    )
}
