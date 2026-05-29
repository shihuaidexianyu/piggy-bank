package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
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
                AssetFlowCard(state = state)
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
    val assetAccent = when {
        state.assetChange > 0L -> income
        state.assetChange < 0L -> expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val adjustmentAccent = when {
        state.assetAdjustment > 0L -> income
        state.assetAdjustment < 0L -> expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    MoneyCard {
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
                    text = state.selectedPeriod.displayName,
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowSummaryMetric(
                label = "日常结余",
                value = state.netCashFlowText,
                accent = netAccent,
                modifier = Modifier.weight(1f),
            )
            FlowSummaryMetric(
                label = "资产变化",
                value = state.assetChangeText,
                accent = assetAccent.takeUnless { it == MaterialTheme.colorScheme.onSurfaceVariant } ?: current,
                modifier = Modifier.weight(1f),
            )
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
    val topNodeCenterY = 39.dp
    val middleNodeCenterY = 135.dp
    val bottomNodeCenterY = 231.dp
    val topNodeBottomY = 78.dp
    val middleNodeTopY = 96.dp
    val middleNodeBottomY = 174.dp
    val bottomNodeTopY = 192.dp
    val branchY = 86.dp
    val mergeY = 184.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(270.dp)) {
            val oneSixth = size.width / 6f
            val centerX = size.width / 2f
            val fiveSixths = size.width * 5f / 6f
            val topNodeBottom = topNodeBottomY.toPx()
            val middleNodeTop = middleNodeTopY.toPx()
            val middleNodeBottom = middleNodeBottomY.toPx()
            val bottomNodeTop = bottomNodeTopY.toPx()
            val branch = branchY.toPx()
            val merge = mergeY.toPx()

            drawLine(
                color = lineColor,
                start = Offset(oneSixth, topNodeBottom),
                end = Offset(oneSixth, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(fiveSixths, topNodeBottom),
                end = Offset(fiveSixths, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(oneSixth, branch),
                end = Offset(fiveSixths, branch),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(centerX, branch),
                end = Offset(centerX, middleNodeTop),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            listOf(oneSixth, centerX, fiveSixths).forEach { x ->
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
                start = Offset(oneSixth, merge),
                end = Offset(fiveSixths, merge),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(centerX, merge),
                end = Offset(centerX, bottomNodeTop),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (topNodeCenterY - 39.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowNode(
                label = "收入",
                value = "+${state.totalInflowText}",
                accent = incomeAccent,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
            FlowNode(
                label = "支出",
                value = "-${state.totalOutflowText}",
                accent = expenseAccent,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (middleNodeCenterY - 39.dp)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowNode(
                label = "期初资产",
                value = state.openingAssetsText,
                accent = currentAccent,
                modifier = Modifier.weight(1f),
            )
            FlowNode(
                label = "净流入",
                value = state.netCashFlowText,
                accent = netAccent,
                modifier = Modifier.weight(1f),
            )
            FlowNode(
                label = "资产校准",
                value = state.assetAdjustmentText,
                accent = adjustmentAccent,
                modifier = Modifier.weight(1f),
            )
        }
        FlowNode(
            label = "期末资产",
            value = state.closingAssetsText,
            accent = currentAccent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (bottomNodeCenterY - 39.dp))
                .widthIn(min = 128.dp, max = 180.dp),
        )
    }
}

@Composable
private fun FlowNode(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(78.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
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
                style = when {
                    value.length > 15 -> MaterialTheme.typography.labelMedium
                    value.length > 11 -> MaterialTheme.typography.labelLarge
                    else -> MaterialTheme.typography.bodyMedium
                },
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlowSummaryMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = when {
                    value.length > 18 -> MaterialTheme.typography.labelLarge
                    value.length > 13 -> MaterialTheme.typography.bodyMedium
                    else -> MaterialTheme.typography.titleMedium
                },
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
