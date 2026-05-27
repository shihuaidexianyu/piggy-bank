package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.ui.common.AccountColorSwatch
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
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
                StatsSummaryCard(state = state)
            }
            item {
                MoneySectionHeader(title = "支出用途", trailing = state.rangeText)
            }
            item {
                if (state.purposeBreakdown.isEmpty()) {
                    MoneyEmptyStateCard(
                        title = "暂无支出记录",
                        subtitle = "这个时间段还没有出账，稍后会在这里看到用途排行。",
                    )
                } else {
                    PurposeBreakdownList(
                        items = state.purposeBreakdown,
                        accent = LocalMoneyColors.current.expense,
                    )
                }
            }
            item {
                MoneySectionHeader(title = "现金流趋势")
            }
            item {
                TrendCard(points = state.trendPoints)
            }
            item {
                MoneySectionHeader(title = "账户分布")
            }
            item {
                if (state.accountBalances.isEmpty()) {
                    MoneyEmptyStateCard(
                        title = "暂无账户",
                        subtitle = "创建账户后，这里会显示当前余额分布。",
                    )
                } else {
                    AccountBalanceList(items = state.accountBalances)
                }
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
private fun StatsSummaryCard(state: StatsUiState) {
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

    MoneyCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = state.selectedPeriod.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "转账不计入收支",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric(
                    label = "收入",
                    value = state.totalInflowText,
                    accent = income,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "支出",
                    value = state.totalOutflowText,
                    accent = expense,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric(
                    label = "结余",
                    value = state.netCashFlowText,
                    accent = netAccent,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "资产变化",
                    value = state.assetChangeText,
                    accent = assetAccent.takeUnless { it == MaterialTheme.colorScheme.onSurfaceVariant } ?: current,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = when {
                    value.length > 18 -> MaterialTheme.typography.bodyMedium
                    value.length > 13 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleLarge
                },
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun PurposeBreakdownList(
    items: List<StatsPurposeUiModel>,
    accent: Color,
) {
    MoneyListSection {
        items.forEachIndexed { index, item ->
            RankingRow(
                title = item.purpose,
                value = item.amountText,
                share = item.share,
                accent = accent,
            )
            if (index != items.lastIndex) {
                MoneySectionDivider()
            }
        }
    }
}

@Composable
private fun AccountBalanceList(items: List<StatsAccountUiModel>) {
    MoneyListSection {
        items.forEachIndexed { index, item ->
            RankingRow(
                title = item.name,
                value = item.balanceText,
                share = item.share,
                accent = MaterialTheme.colorScheme.primary,
                leading = {
                    AccountColorSwatch(colorName = item.colorName, size = 18.dp)
                },
            )
            if (index != items.lastIndex) {
                MoneySectionDivider()
            }
        }
    }
}

@Composable
private fun RankingRow(
    title: String,
    value: String,
    share: Float,
    accent: Color,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        ProgressBar(
            share = share,
            accent = accent,
        )
    }
}

@Composable
private fun ProgressBar(
    share: Float,
    accent: Color,
) {
    val fraction = share.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .background(accent),
            )
        }
    }
}

@Composable
private fun TrendCard(points: List<StatsTrendUiPoint>) {
    MoneyCard {
        if (points.isEmpty() || points.all { it.inflow == 0L && it.outflow == 0L }) {
            Text(
                text = "这个时间段还没有现金流记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MoneyCard
        }
        TrendChart(
            points = points,
            incomeColor = LocalMoneyColors.current.income,
            expenseColor = LocalMoneyColors.current.expense,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(label = "收入", color = LocalMoneyColors.current.income)
            LegendItem(label = "支出", color = LocalMoneyColors.current.expense)
        }
    }
}

@Composable
private fun TrendChart(
    points: List<StatsTrendUiPoint>,
    incomeColor: Color,
    expenseColor: Color,
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = points.maxOf { maxOf(it.inflow, it.outflow) }.coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
    ) {
        val chartHeight = size.height - 26.dp.toPx()
        val baseline = chartHeight
        val slotWidth = size.width / points.size
        drawLine(
            color = axisColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        points.forEachIndexed { index, point ->
            val centerX = slotWidth * index + slotWidth / 2f
            val barWidth = (slotWidth * 0.24f).coerceIn(2.dp.toPx(), 8.dp.toPx())
            val inflowHeight = chartHeight * (point.inflow.toFloat() / maxValue)
            val outflowHeight = chartHeight * (point.outflow.toFloat() / maxValue)
            drawRoundRect(
                color = incomeColor,
                topLeft = Offset(centerX - barWidth - 1.dp.toPx(), baseline - inflowHeight),
                size = Size(barWidth, inflowHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            drawRoundRect(
                color = expenseColor,
                topLeft = Offset(centerX + 1.dp.toPx(), baseline - outflowHeight),
                size = Size(barWidth, outflowHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        points.filterIndexed { index, _ ->
            when {
                points.size <= 12 -> true
                index == 0 || index == points.lastIndex -> true
                (index + 1) % 5 == 0 -> true
                else -> false
            }
        }.forEach { point ->
            Text(
                text = point.label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
