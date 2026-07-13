package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import java.math.BigInteger

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetRange: () -> Unit,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val loadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = stringResource(R.string.stats_title),
            trailing = {
                CompactMonthNavigator(
                    rangeText = state.rangeText,
                    canNavigateNext = state.canNavigateNext,
                    onPrevious = onPreviousRange,
                    onNext = onNextRange,
                    onCurrent = onResetRange,
                )
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 12.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MoneyDimens.bottomNavContentPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (val content = state.toAsyncContent(loadErrorMessage)) {
                AsyncContent.Loading,
                is AsyncContent.Error,
                -> item {
                    AsyncContentRenderer(
                        content = content,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                        data = { _, _ -> },
                    )
                }
                is AsyncContent.Empty -> item {
                    MoneyEmptyStateCard(
                        title = stringResource(R.string.stats_empty_title),
                        subtitle = stringResource(R.string.stats_empty_description),
                    )
                }
                is AsyncContent.Data,
                is AsyncContent.Refreshing,
                -> {
                    item { MonthHero(state, onOpenHistory) }
                    item { AssetFlowSection(state, onOpenHistory) }
                    item { DailyTrendSection(state.dailyPoints, onOpenHistory) }
                }
            }
        }
    }
}

@Composable
private fun CompactMonthNavigator(
    rangeText: String,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.stats_previous_month),
                )
            }
            Text(
                text = rangeText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onCurrent)
                    .padding(horizontal = 2.dp, vertical = 10.dp),
            )
            IconButton(
                onClick = onNext,
                enabled = canNavigateNext,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stats_next_month),
                )
            }
        }
    }
}

@Composable
private fun MonthHero(
    state: StatsUiState,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val changeColor = when {
        state.assetChange > 0L -> moneyColors.income
        state.assetChange < 0L -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.stats_closing_assets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.closingAssetsText,
                    style = if (state.closingAssetsText.length > 14) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.stats_month_asset_change, state.assetChangeText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroMetric(
                    label = stringResource(R.string.stats_income),
                    value = state.totalInflowText,
                    accent = moneyColors.income,
                    onClick = { onOpenHistory(state.inflowHistoryFilters) },
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = stringResource(R.string.stats_expense),
                    value = state.totalOutflowText,
                    accent = moneyColors.expense,
                    onClick = { onOpenHistory(state.outflowHistoryFilters) },
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = stringResource(R.string.stats_net_cash_flow),
                    value = state.netCashFlowText,
                    accent = when {
                        state.netCashFlow > 0L -> moneyColors.income
                        state.netCashFlow < 0L -> moneyColors.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onOpenHistory(state.netCashFlowHistoryFilters) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssetFlowSection(
    state: StatsUiState,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val netAccent = when {
        state.netCashFlow > 0L -> moneyColors.income
        state.netCashFlow < 0L -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val adjustmentAccent = when {
        state.assetAdjustment > 0L -> moneyColors.income
        state.assetAdjustment < 0L -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AnalysisSectionHeader(
        title = stringResource(R.string.stats_flow_title),
        hint = stringResource(R.string.stats_flow_hint),
    )
    MoneyCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
        AssetFlowDiagram(
            state = state,
            incomeAccent = moneyColors.income,
            expenseAccent = moneyColors.expense,
            currentAccent = moneyColors.current,
            netAccent = netAccent,
            adjustmentAccent = adjustmentAccent,
            onOpenHistory = onOpenHistory,
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
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val layout = remember(maxWidth) { calculateAssetFlowLayout(maxWidth.value) }
        Box(modifier = Modifier.fillMaxWidth().height(layout.diagramHeight)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(layout.diagramHeight)) {
                val nodeWidth = layout.nodeWidth.toPx()
                val centerX = size.width / 2f
                val leftX = nodeWidth / 2f
                val rightX = size.width - nodeWidth / 2f
                val topBottom = layout.nodeHeight.toPx()
                val middleTop = layout.middleRowTop.toPx()
                val middleBottom = (layout.middleRowTop + layout.nodeHeight).toPx()
                val bottomTop = layout.bottomRowTop.toPx()
                val branchY = with(density) { layout.branchY.toPx() }
                val mergeY = with(density) { layout.mergeY.toPx() }
                val stroke = 1.dp.toPx()

                drawFlowLine(leftX, topBottom, leftX, branchY, lineColor, stroke)
                drawFlowLine(rightX, topBottom, rightX, branchY, lineColor, stroke)
                drawFlowLine(leftX, branchY, rightX, branchY, lineColor, stroke)
                drawFlowLine(centerX, branchY, centerX, middleTop, lineColor, stroke)
                listOf(leftX, centerX, rightX).forEach { x ->
                    drawFlowLine(x, middleBottom, x, mergeY, lineColor, stroke)
                }
                drawFlowLine(leftX, mergeY, rightX, mergeY, lineColor, stroke)
                drawFlowLine(centerX, mergeY, centerX, bottomTop, lineColor, stroke)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FlowNode(
                    label = stringResource(R.string.stats_flow_cash_in),
                    value = state.totalInflowText,
                    accent = incomeAccent,
                    nodeHeight = layout.nodeHeight,
                    onClick = { onOpenHistory(state.inflowHistoryFilters) },
                    modifier = Modifier.width(layout.nodeWidth),
                )
                FlowNode(
                    label = stringResource(R.string.stats_flow_cash_out),
                    value = state.totalOutflowText,
                    accent = expenseAccent,
                    nodeHeight = layout.nodeHeight,
                    onClick = { onOpenHistory(state.outflowHistoryFilters) },
                    modifier = Modifier.width(layout.nodeWidth),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = layout.middleRowTop),
                horizontalArrangement = Arrangement.spacedBy(layout.middleGap),
            ) {
                FlowNode(
                    label = stringResource(R.string.stats_flow_opening_assets),
                    value = state.openingAssetsText,
                    accent = currentAccent,
                    nodeHeight = layout.nodeHeight,
                    modifier = Modifier.width(layout.nodeWidth),
                )
                FlowNode(
                    label = stringResource(R.string.stats_flow_net_cash),
                    value = state.netCashFlowText,
                    accent = netAccent,
                    nodeHeight = layout.nodeHeight,
                    onClick = { onOpenHistory(state.netCashFlowHistoryFilters) },
                    modifier = Modifier.width(layout.nodeWidth),
                )
                FlowNode(
                    label = stringResource(R.string.stats_flow_adjustments),
                    value = state.assetAdjustmentText,
                    accent = adjustmentAccent,
                    nodeHeight = layout.nodeHeight,
                    modifier = Modifier.width(layout.nodeWidth),
                )
            }
            FlowNode(
                label = stringResource(R.string.stats_flow_closing_assets),
                value = state.closingAssetsText,
                accent = currentAccent,
                nodeHeight = layout.nodeHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = layout.bottomRowTop)
                    .width(layout.bottomNodeWidth),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowLine(
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
    onClick: (() -> Unit)? = null,
) {
    val semantics = stringResource(R.string.stats_flow_node_semantics, label, value)
    Surface(
        modifier = modifier
            .height(nodeHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = semantics
                if (onClick != null) role = Role.Button
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
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
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DailyTrendSection(
    points: List<StatsDailyUiModel>,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    val activeCount = points.count { it.inflow != 0L || it.outflow != 0L }
    var selectedIndex by remember(points) {
        mutableIntStateOf(
            points.indexOfLast { it.inflow != 0L || it.outflow != 0L }
                .takeIf { it >= 0 }
                ?: points.lastIndex.coerceAtLeast(0),
        )
    }
    AnalysisSectionHeader(
        title = stringResource(R.string.stats_daily_trend),
        hint = stringResource(R.string.stats_daily_combined_hint),
    )
    if (points.isEmpty() || activeCount == 0) {
        AnalysisInlineEmpty(stringResource(R.string.stats_daily_no_activity))
        return
    }
    val selected = points[selectedIndex.coerceIn(points.indices)]
    MoneyCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        SelectedDaySummary(
            point = selected,
            canPrevious = selectedIndex > 0,
            canNext = selectedIndex < points.lastIndex,
            onPrevious = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
            onNext = { selectedIndex = (selectedIndex + 1).coerceAtMost(points.lastIndex) },
            onClick = { onOpenHistory(selected.historyFilters) },
        )
        DailyTrendLegend()
        DailyTrendCharts(
            points = points,
            selectedIndex = selectedIndex,
            onSelected = { selectedIndex = it },
        )
    }
}

@Composable
private fun DailyTrendLegend() {
    val moneyColors = LocalMoneyColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrendLegendItem(stringResource(R.string.stats_income), moneyColors.income)
        TrendLegendItem(stringResource(R.string.stats_expense), moneyColors.expense)
    }
}

@Composable
private fun TrendLegendItem(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, MaterialTheme.shapes.small))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Suppress("FloatingPointUsageInMoney")
@Composable
private fun DailyTrendCharts(
    points: List<StatsDailyUiModel>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val cumulative = remember(points) {
        var running = 0L
        points.map { point ->
            running = ledgerAddExact(running, point.netFlow)
            running
        }
    }
    val maxDailyMagnitude = remember(points) {
        points.flatMap { listOf(it.inflow, it.outflow) }
            .maxOfOrNull { BigInteger.valueOf(it).abs() }
            ?.coerceAtLeast(BigInteger.ONE)
            ?: BigInteger.ONE
    }
    val maxCumulativeMagnitude = remember(cumulative) {
        cumulative.maxOfOrNull { BigInteger.valueOf(it).abs() }
            ?.coerceAtLeast(BigInteger.ONE)
            ?: BigInteger.ONE
    }
    val semantics = stringResource(R.string.stats_daily_chart_semantics, points.size, points.count { it.inflow != 0L || it.outflow != 0L })
    Column(
        modifier = Modifier.semantics { contentDescription = semantics },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_daily_cash_bars),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DailyBarsCanvas(
            points = points,
            selectedIndex = selectedIndex,
            maxDailyMagnitude = maxDailyMagnitude,
            onSelected = onSelected,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stats_cumulative_net),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.stats_independent_scale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CumulativeLineCanvas(
            cumulative = cumulative,
            selectedIndex = selectedIndex,
            maxCumulativeMagnitude = maxCumulativeMagnitude,
            onSelected = onSelected,
        )
        DayAxisLabels(points)
    }
}

@Suppress("FloatingPointUsageInMoney")
@Composable
private fun DailyBarsCanvas(
    points: List<StatsDailyUiModel>,
    selectedIndex: Int,
    maxDailyMagnitude: BigInteger,
    onSelected: (Int) -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val primary = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val selection = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        ChartAxisLabels()
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(start = 22.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * points.size).toInt().coerceIn(points.indices)
                        onSelected(index)
                    }
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSelected(((offset.x / size.width) * points.size).toInt().coerceIn(points.indices))
                        },
                        onDrag = { change, _ ->
                            onSelected(((change.position.x / size.width) * points.size).toInt().coerceIn(points.indices))
                        },
                    )
                },
        ) {
        val topPadding = 8.dp.toPx()
        val bottomPadding = 8.dp.toPx()
        val baseline = size.height / 2f
        val halfHeight = (size.height - topPadding - bottomPadding) / 2f
        val slotWidth = size.width / points.size
        val barWidth = (slotWidth * 0.72f).coerceIn(3.dp.toPx(), 12.dp.toPx())
        val maxDailyAsFloat = maxDailyMagnitude.toFloat()
        fun barHeightFor(amount: Long): Float =
            BigInteger.valueOf(amount).abs().toFloat() / maxDailyAsFloat * halfHeight

        val selectedLeft = selectedIndex.coerceIn(points.indices) * slotWidth
        drawRect(selection, topLeft = Offset(selectedLeft, 0f), size = androidx.compose.ui.geometry.Size(slotWidth, size.height))
        drawLine(guide, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.5.dp.toPx())
        drawLine(guide.copy(alpha = 0.5f), Offset(0f, topPadding), Offset(size.width, topPadding), strokeWidth = 1.dp.toPx())
        drawLine(guide.copy(alpha = 0.5f), Offset(0f, size.height - bottomPadding), Offset(size.width, size.height - bottomPadding), strokeWidth = 1.dp.toPx())

        points.forEachIndexed { index, point ->
            val center = index * slotWidth + slotWidth / 2f
            val incomeHeight = barHeightFor(point.inflow)
            val expenseHeight = barHeightFor(point.outflow)
            if (incomeHeight > 0f) {
                drawRoundRect(
                    color = moneyColors.income,
                    topLeft = Offset(center - barWidth / 2f, baseline - incomeHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, incomeHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
            if (expenseHeight > 0f) {
                drawRoundRect(
                    color = moneyColors.expense,
                    topLeft = Offset(center - barWidth / 2f, baseline),
                    size = androidx.compose.ui.geometry.Size(barWidth, expenseHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        val selectedCenter = selectedLeft + slotWidth / 2f
        drawLine(
            color = primary.copy(alpha = 0.72f),
            start = Offset(selectedCenter, 0f),
            end = Offset(selectedCenter, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        }
    }
}

@Suppress("FloatingPointUsageInMoney")
@Composable
private fun CumulativeLineCanvas(
    cumulative: List<Long>,
    selectedIndex: Int,
    maxCumulativeMagnitude: BigInteger,
    onSelected: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    val selection = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    Box(modifier = Modifier.fillMaxWidth().height(76.dp)) {
        ChartAxisLabels()
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 22.dp)
                .pointerInput(cumulative) {
                    detectTapGestures { offset ->
                        onSelected(((offset.x / size.width) * cumulative.size).toInt().coerceIn(cumulative.indices))
                    }
                }
                .pointerInput(cumulative) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSelected(((offset.x / size.width) * cumulative.size).toInt().coerceIn(cumulative.indices))
                        },
                        onDrag = { change, _ ->
                            onSelected(((change.position.x / size.width) * cumulative.size).toInt().coerceIn(cumulative.indices))
                        },
                    )
                },
        ) {
        val verticalPadding = 8.dp.toPx()
        val baseline = size.height / 2f
        val halfHeight = (size.height - verticalPadding * 2f) / 2f
        val slotWidth = size.width / cumulative.size
        val maxAsFloat = maxCumulativeMagnitude.toFloat()
        fun lineYFor(amount: Long): Float = baseline - amount.toFloat() / maxAsFloat * halfHeight
        val selectedLeft = selectedIndex.coerceIn(cumulative.indices) * slotWidth
        drawRect(selection, topLeft = Offset(selectedLeft, 0f), size = androidx.compose.ui.geometry.Size(slotWidth, size.height))
        drawLine(guide, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.5.dp.toPx())
        val path = Path()
        cumulative.forEachIndexed { index, amount ->
            val x = index * slotWidth + slotWidth / 2f
            val y = lineYFor(amount)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val pointX = selectedIndex.coerceIn(cumulative.indices) * slotWidth + slotWidth / 2f
        val pointY = lineYFor(cumulative[selectedIndex.coerceIn(cumulative.indices)])
        drawCircle(color = primary, radius = 4.dp.toPx(), center = Offset(pointX, pointY))
        drawLine(
            color = primary.copy(alpha = 0.72f),
            start = Offset(pointX, 0f),
            end = Offset(pointX, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        }
    }
}

@Composable
private fun BoxScope.ChartAxisLabels() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(R.string.stats_axis_positive),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.align(Alignment.TopStart),
    )
    Text(
        text = stringResource(R.string.stats_axis_zero),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.align(Alignment.CenterStart),
    )
    Text(
        text = stringResource(R.string.stats_axis_negative),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.align(Alignment.BottomStart),
    )
}

@Composable
private fun DayAxisLabels(points: List<StatsDailyUiModel>) {
    val last = points.lastIndex
    val indices = listOf(0, last / 3, last * 2 / 3, last).distinct()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        indices.forEach { index ->
            Text(
                text = stringResource(R.string.stats_day_axis_label, points[index].date.dayOfMonth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedDaySummary(
    point: StatsDailyUiModel,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalMoneyColors.current
    val semantics = stringResource(
        R.string.stats_daily_row_semantics,
        point.dateText,
        point.inflowText,
        point.outflowText,
        point.netFlowText,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = canPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.stats_previous_day),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                        .semantics(mergeDescendants = true) {
                            contentDescription = semantics
                            role = Role.Button
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(point.dateText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.stats_view_details), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.stats_next_day),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InlineMetric(stringResource(R.string.stats_income), point.inflowText, colors.income, Modifier.weight(1f))
                InlineMetric(stringResource(R.string.stats_expense), point.outflowText, colors.expense, Modifier.weight(1f))
                InlineMetric(
                    stringResource(R.string.stats_net_cash_flow),
                    point.netFlowText,
                    when {
                        point.netFlow > 0L -> colors.income
                        point.netFlow < 0L -> colors.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InlineMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountCashFlowSection(
    accounts: List<StatsAccountCashFlowUiModel>,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    AnalysisSectionHeader(
        title = stringResource(R.string.stats_account_cash_section),
        hint = stringResource(R.string.stats_account_cash_hint),
    )
    if (accounts.isEmpty()) {
        AnalysisInlineEmpty(stringResource(R.string.stats_no_account_cash))
        return
    }
    val maximum = remember(accounts) {
        accounts.maxOfOrNull { BigInteger.valueOf(it.inflow).add(BigInteger.valueOf(it.outflow)) }
            ?.coerceAtLeast(BigInteger.ONE)
            ?: BigInteger.ONE
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
    ) {
        Column {
            accounts.forEachIndexed { index, account ->
                AccountFlowRow(account, maximum, onOpenHistory)
                if (index != accounts.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Suppress("FloatingPointUsageInMoney")
@Composable
private fun AccountFlowRow(
    account: StatsAccountCashFlowUiModel,
    maximum: BigInteger,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    val colors = LocalMoneyColors.current
    val total = BigInteger.valueOf(account.inflow).add(BigInteger.valueOf(account.outflow))
    val usedFraction = total.toFloat() / maximum.toFloat()
    val incomeFraction = if (total.signum() == 0) 0f else BigInteger.valueOf(account.inflow).toFloat() / total.toFloat()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.stats_account_net_format, account.netFlowText),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    account.netFlow > 0L -> colors.income
                    account.netFlow < 0L -> colors.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
        ) {
            Row(modifier = Modifier.fillMaxWidth(usedFraction.coerceIn(0f, 1f)).height(9.dp)) {
                if (incomeFraction > 0f) {
                    Box(modifier = Modifier.weight(incomeFraction).height(9.dp).background(colors.income))
                }
                if (incomeFraction < 1f) {
                    Box(modifier = Modifier.weight(1f - incomeFraction).height(9.dp).background(colors.expense))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AccountExactMetric(
                label = stringResource(R.string.stats_income),
                value = account.inflowText,
                accent = colors.income,
                onClick = { onOpenHistory(account.inflowHistoryFilters) },
                modifier = Modifier.weight(1f),
            )
            AccountExactMetric(
                label = stringResource(R.string.stats_expense),
                value = account.outflowText,
                accent = colors.expense,
                onClick = { onOpenHistory(account.outflowHistoryFilters) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccountExactMetric(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TransferPathsSection(
    state: StatsUiState,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    AnalysisSectionHeader(
        title = stringResource(R.string.stats_transfer_path_section),
        hint = if (state.transferPaths.isEmpty()) {
            stringResource(R.string.stats_transfer_path_hint)
        } else {
            stringResource(R.string.stats_transfer_summary, state.totalTransferText, state.transferPaths.size)
        },
    )
    if (state.transferPaths.isEmpty()) {
        AnalysisInlineEmpty(stringResource(R.string.stats_no_transfers))
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
    ) {
        Column {
            state.transferPaths.forEachIndexed { index, path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHistory(path.historyFilters) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = LocalMoneyColors.current.transfer.copy(alpha = 0.10f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = LocalMoneyColors.current.transfer,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
                    Text(path.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        path.amountText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalMoneyColors.current.transfer,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (index != state.transferPaths.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun AnalysisSectionHeader(title: String, hint: String) {
    MoneySectionHeader(
        title = title,
        trailingContent = {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    )
}

@Composable
private fun AnalysisInlineEmpty(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}
