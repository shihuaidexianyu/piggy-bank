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
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 112.dp),
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
                text = "转账不计入收支",
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        val layout = remember(maxWidth, density) {
            with(density) { calculateAssetFlowLayout(maxWidth.toPx()) }
        }
        val middleGap = with(density) { layout.middleGap.toDp() }
        val middleNodeWidth = with(density) { layout.middleNodeWidth.toDp() }
        val topNodeWidth = with(density) { layout.topNodeWidth.toDp() }
        val bottomNodeWidth = with(density) { layout.bottomNodeWidth.toDp() }
        val nodeHeight = with(density) { layout.nodeHeight.toDp() }
        val middleNodeTop = with(density) { layout.middleNodeTop.toDp() }
        val bottomNodeTop = with(density) { layout.bottomNodeTop.toDp() }
        val diagramHeight = with(density) { layout.diagramHeight.toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(diagramHeight),
        ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(diagramHeight)) {
            val topNodeWidthPx = topNodeWidth.toPx()
            val middleNodeWidthPx = middleNodeWidth.toPx()
            val topLeftX = topNodeWidthPx / 2f
            val centerX = size.width / 2f
            val topRightX = size.width - topNodeWidthPx / 2f
            val leftMiddleX = middleNodeWidthPx / 2f
            val rightMiddleX = size.width - middleNodeWidthPx / 2f
            val topNodeBottom = nodeHeight.toPx()
            val middleNodeTopPx = middleNodeTop.toPx()
            val middleNodeBottom = (middleNodeTop + nodeHeight).toPx()
            val bottomNodeTopPx = bottomNodeTop.toPx()
            val branch = layout.branchY
            val merge = layout.mergeY

            drawLine(
                color = lineColor,
                start = Offset(topLeftX, topNodeBottom),
                end = Offset(topLeftX, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(topRightX, topNodeBottom),
                end = Offset(topRightX, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(topLeftX, branch),
                end = Offset(topRightX, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(centerX, branch),
                end = Offset(centerX, middleNodeTopPx),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            listOf(leftMiddleX, centerX, rightMiddleX).forEach { x ->
                drawLine(
                    color = lineColor,
                    start = Offset(x, middleNodeBottom),
                    end = Offset(x, merge),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = lineColor,
                start = Offset(leftMiddleX, merge),
                end = Offset(rightMiddleX, merge),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(centerX, merge),
                end = Offset(centerX, bottomNodeTopPx),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FlowNode(
                label = "收入",
                value = state.totalInflowFlowText,
                accent = incomeAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier.width(topNodeWidth),
            )
            FlowNode(
                label = "支出",
                value = state.totalOutflowFlowText,
                accent = expenseAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier.width(topNodeWidth),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = middleNodeTop),
            horizontalArrangement = Arrangement.spacedBy(middleGap),
        ) {
            FlowNode(
                label = "期初资产",
                value = state.openingAssetsFlowText,
                accent = currentAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier.width(middleNodeWidth),
            )
            FlowNode(
                label = "净流入",
                value = state.netCashFlowFlowText,
                accent = netAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier.width(middleNodeWidth),
            )
            FlowNode(
                label = "资产调整",
                value = state.assetAdjustmentFlowText,
                accent = adjustmentAccent,
                nodeHeight = nodeHeight,
                modifier = Modifier.width(middleNodeWidth),
            )
        }
        FlowNode(
            label = "期末资产",
            value = state.closingAssetsFlowText,
            accent = currentAccent,
            nodeHeight = nodeHeight,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = bottomNodeTop)
                .width(bottomNodeWidth),
        )
        }
    }
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
