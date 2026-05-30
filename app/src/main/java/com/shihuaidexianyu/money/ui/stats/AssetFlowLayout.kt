package com.shihuaidexianyu.money.ui.stats

data class AssetFlowLayoutSpec(
    val middleGap: Float,
    val middleNodeWidth: Float,
    val topNodeWidth: Float,
    val bottomNodeWidth: Float,
    val nodeHeight: Float,
    val rowGap: Float,
    val middleNodeTop: Float,
    val bottomNodeTop: Float,
    val branchY: Float,
    val mergeY: Float,
    val diagramHeight: Float,
)

fun calculateAssetFlowLayout(width: Float): AssetFlowLayoutSpec {
    val middleGap = (width * 0.012f).coerceIn(4f, 6f)
    val middleNodeWidth = (width - middleGap * 2f) / 3f
    val topNodeWidth = middleNodeWidth
    val bottomNodeWidth = middleNodeWidth * 1.36f
    val nodeHeight = (middleNodeWidth * 0.48f).coerceIn(48f, 56f)
    val rowGap = (nodeHeight * 0.46f).coerceIn(20f, 26f)
    val middleNodeTop = nodeHeight + rowGap
    val bottomNodeTop = middleNodeTop + nodeHeight + rowGap
    val branchY = nodeHeight + rowGap / 2f
    val mergeY = middleNodeTop + nodeHeight + rowGap / 2f
    val diagramHeight = bottomNodeTop + nodeHeight

    return AssetFlowLayoutSpec(
        middleGap = middleGap,
        middleNodeWidth = middleNodeWidth,
        topNodeWidth = topNodeWidth,
        bottomNodeWidth = bottomNodeWidth,
        nodeHeight = nodeHeight,
        rowGap = rowGap,
        middleNodeTop = middleNodeTop,
        bottomNodeTop = bottomNodeTop,
        branchY = branchY,
        mergeY = mergeY,
        diagramHeight = diagramHeight,
    )
}
