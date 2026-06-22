package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "统计",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatsPeriodSelector(
                    selectedPeriod = state.selectedPeriod,
                    onPeriodChange = onPeriodChange,
                )
            }
            item {
                StatsRangeNavigator(
                    rangeText = state.rangeText,
                    isCurrentRange = state.isCurrentRange,
                    onPreviousRange = onPreviousRange,
                    onNextRange = onNextRange,
                    onResetRange = onResetRange,
                )
            }
            item {
                AssetFlowCard(state = state)
            }
        }
    }
}

@Composable
private fun StatsRangeNavigator(
    rangeText: String,
    isCurrentRange: Boolean,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetRange: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(12.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousRange) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "上一期",
                )
            }
            Text(
                text = rangeText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isCurrentRange) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    onClick = onResetRange,
                ) {
                    Text(
                        text = "回到本期",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onNextRange) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "下一期",
                )
            }
        }
    }
}

@Composable
private fun StatsPeriodSelector(
    selectedPeriod: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(12.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsPeriod.entries.forEach { period ->
                val selected = period == selectedPeriod
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(10.dp),
                    onClick = { onPeriodChange(period) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = period.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetFlowCard(state: StatsUiState) {
    val income = LocalMoneyColors.current.income
    val expense = LocalMoneyColors.current.expense
    val current = LocalMoneyColors.current.current
    val netAccent = when {
        state.netCashFlow > 0L -> income
        state.netCashFlow < 0L -> expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val adjustmentAccent = when {
        state.assetAdjustment > 0L -> income
        state.assetAdjustment < 0L -> expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    MoneyCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "${state.selectedPeriod.displayName}视图",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "转账不计入现金流",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        AssetFlowDiagram(
            state = state,
            incomeAccent = income,
            expenseAccent = expense,
            currentAccent = current,
            netAccent = netAccent,
            adjustmentAccent = adjustmentAccent,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MoneyInlineLabelValue(label = "其他变动", value = state.manualAdjustmentText)
            MoneyInlineLabelValue(label = "对账差额", value = state.reconciliationText)
        }
    }
}

@Composable
private fun AssetFlowDiagram(
    state: StatsUiState,
    incomeAccent: Color,
    expenseAccent: Color,
    currentAccent: Color,
    netAccent: Color,
    adjustmentAccent: Color,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.86f)
    val lineStrokeWidth = 1.dp

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        val layout = remember(maxWidth) { calculateAssetFlowLayout(maxWidth.value) }
        val middleGap = layout.middleGap
        val nodeWidth = layout.nodeWidth
        val bottomNodeWidth = layout.bottomNodeWidth
        val nodeHeight = layout.nodeHeight
        val middleRowTop = layout.middleRowTop
        val bottomRowTop = layout.bottomRowTop
        val diagramHeight = layout.diagramHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(diagramHeight),
        ) {
            // === Connector lines ===
            // The diagram has 3 rows of nodes. Lines connect:
            //   Row 1 (top, 2 nodes) → Row 2 (middle, 3 nodes): branch from each top node down,
            //   horizontal merge, then drop to the center middle node.
            //   Row 2 → Row 3 (bottom, 1 node): from each middle node down, horizontal merge,
            //   then drop to the bottom node.
            Canvas(modifier = Modifier.fillMaxWidth().height(diagramHeight)) {
                val nodeWidthPx = nodeWidth.toPx()
                val centerX = size.width / 2f
                val topLeftX = nodeWidthPx / 2f
                val topRightX = size.width - nodeWidthPx / 2f
                val leftMiddleX = nodeWidthPx / 2f
                val rightMiddleX = size.width - nodeWidthPx / 2f
                val topNodeBottom = nodeHeight.toPx()
                val middleRowTopPx = middleRowTop.toPx()
                val middleNodeBottom = (middleRowTop + nodeHeight).toPx()
                val bottomRowTopPx = bottomRowTop.toPx()
                val branch = with(density) { layout.branchY.toPx() }
                val merge = with(density) { layout.mergeY.toPx() }
                val strokePx = lineStrokeWidth.toPx()

                // Row 1 → Row 2: vertical lines from each top node down to branch line.
                drawConnector(topLeftX, topNodeBottom, topLeftX, branch, lineColor, strokePx)
                drawConnector(topRightX, topNodeBottom, topRightX, branch, lineColor, strokePx)
                // Branch horizontal line connecting the two top nodes.
                drawConnector(topLeftX, branch, topRightX, branch, lineColor, strokePx)
                // Drop from branch center to middle row center node.
                drawConnector(centerX, branch, centerX, middleRowTopPx, lineColor, strokePx)

                // Row 2 → Row 3: vertical lines from each middle node down to merge line.
                listOf(leftMiddleX, centerX, rightMiddleX).forEach { x ->
                    drawConnector(x, middleNodeBottom, x, merge, lineColor, strokePx)
                }
                // Merge horizontal line connecting the three middle nodes.
                drawConnector(leftMiddleX, merge, rightMiddleX, merge, lineColor, strokePx)
                // Drop from merge center to bottom row node.
                drawConnector(centerX, merge, centerX, bottomRowTopPx, lineColor, strokePx)
            }

            // === Nodes ===
            // Row 1: cash inflow + cash outflow (space-between).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowNode(
                    label = "现金入账",
                    value = state.totalInflowFlowText,
                    accent = incomeAccent,
                    nodeHeight = nodeHeight,
                    modifier = Modifier.width(nodeWidth),
                )
                FlowNode(
                    label = "现金出账",
                    value = state.totalOutflowFlowText,
                    accent = expenseAccent,
                    nodeHeight = nodeHeight,
                    modifier = Modifier.width(nodeWidth),
                )
            }
            // Row 2: opening assets + net cash + adjustments (equal width with gap).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = middleRowTop),
                horizontalArrangement = Arrangement.spacedBy(middleGap),
            ) {
                FlowNode(
                    label = "期初资产",
                    value = state.openingAssetsFlowText,
                    accent = currentAccent,
                    nodeHeight = nodeHeight,
                    modifier = Modifier.width(nodeWidth),
                )
                FlowNode(
                    label = "现金净额",
                    value = state.netCashFlowFlowText,
                    accent = netAccent,
                    nodeHeight = nodeHeight,
                    modifier = Modifier.width(nodeWidth),
                )
                FlowNode(
                    label = "调账/对账",
                    value = state.assetAdjustmentFlowText,
                    accent = adjustmentAccent,
                    nodeHeight = nodeHeight,
                    modifier = Modifier.width(nodeWidth),
                )
            }
            // Row 3: closing assets (centered, wider).
            FlowNode(
                label = "期末资产",
                value = state.closingAssetsFlowText,
                accent = currentAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = bottomRowTop)
                    .width(bottomNodeWidth),
            )
        }
    }
}

/** Draws a single connector line between two points. Extracted to reduce drawLine boilerplate. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnector(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

@Composable
private fun FlowNode(
    label: String,
    value: String,
    accent: Color,
    nodeHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(nodeHeight),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = maxWidth * 0.02f
            val amountStyle = rememberFittingAmountStyle(
                value = value,
                maxWidth = maxWidth - horizontalPadding * 2,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = value,
                    style = amountStyle,
                    color = accent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun rememberFittingAmountStyle(value: String, maxWidth: Dp): TextStyle {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(value, maxWidth, density, textMeasurer) {
        val maxWidthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val fontSize = (16 downTo 8).firstOrNull { size ->
            val result = textMeasurer.measure(
                text = AnnotatedString(value),
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = size.sp,
                    lineHeight = (size + 2).sp,
                ),
                maxLines = 1,
                softWrap = false,
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            !result.hasVisualOverflow && result.size.width <= maxWidthPx
        } ?: 8
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 2).sp,
        )
    }
}
