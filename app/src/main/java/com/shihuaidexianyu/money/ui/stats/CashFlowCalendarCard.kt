package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.usecase.CashFlowEvent
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.AmountFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

private val InflowRed = Color(0xFFE14848)
private val OutflowGreen = Color(0xFF1F9D55)
private val NeutralGray = Color(0xFF94A3B8)

@Composable
fun CashFlowCalendarCard(
    settings: AppSettings,
    events: List<CashFlowEvent>,
    mode: CashFlowCardMode,
    granularity: CashFlowGranularity,
    displayUnit: CashFlowDisplayUnit,
    selectedEpochDay: Long,
    visibleEpochDay: Long,
    onModeChange: (CashFlowCardMode) -> Unit,
    onGranularityChange: (CashFlowGranularity) -> Unit,
    onDisplayUnitChange: (CashFlowDisplayUnit) -> Unit,
    onDateSelect: (Long) -> Unit,
    onShiftPeriod: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val visibleDate = LocalDate.ofEpochDay(visibleEpochDay)
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val totalsByDate = remember(events, zoneId) { buildDailyTotals(events, zoneId) }
    val visibleMonth = remember(visibleDate) { YearMonth.from(visibleDate) }
    val buckets = remember(totalsByDate, granularity, visibleDate) {
        buildBuckets(
            totalsByDate = totalsByDate,
            granularity = granularity,
            visibleDate = visibleDate,
        )
    }
    val selectedBucket = remember(buckets, selectedDate) {
        buckets.firstOrNull { selectedDate in it.startDate..it.endDate } ?: buckets.firstOrNull()
    }
    val totalMovement = buckets.sumOf { it.inflow + it.outflow }.coerceAtLeast(1L)
    val monthStatus = remember(totalsByDate, visibleMonth, today, settings) {
        buildMonthStatus(
            totalsByDate = totalsByDate,
            visibleMonth = visibleMonth,
            today = today,
            settings = settings,
        )
    }

    MoneyCard(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "现金流日历",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            monthStatus?.let { status ->
                MoneyStatusPill(
                    text = status,
                    accent = Color(0xFF2B6EF2),
                )
            }
        }

        CashFlowControlRow(
            mode = mode,
            granularity = granularity,
            displayUnit = displayUnit,
            onModeChange = onModeChange,
            onGranularityChange = onGranularityChange,
            onDisplayUnitChange = onDisplayUnitChange,
        )

        when (mode) {
            CashFlowCardMode.CALENDAR -> {
                CalendarHeader(
                    text = headerText(visibleDate, granularity),
                    onPrevious = { onShiftPeriod(-1L) },
                    onNext = { onShiftPeriod(1L) },
                )
                CalendarContent(
                    granularity = granularity,
                    today = today,
                    visibleDate = visibleDate,
                    selectedDate = selectedDate,
                    totalsByDate = totalsByDate,
                    totalMovement = totalMovement,
                    displayUnit = displayUnit,
                    settings = settings,
                    onDateSelect = onDateSelect,
                )
            }
            CashFlowCardMode.TREND -> {
                selectedBucket?.let { bucket ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = bucketHeadline(bucket, granularity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatBucketPrimary(bucket, displayUnit, totalMovement, settings),
                            style = MaterialTheme.typography.headlineSmall,
                            color = bucketPrimaryColor(bucket.netAmount),
                        )
                        Text(
                            text = "入账 ${AmountFormatter.format(bucket.inflow, settings)} / 出账 ${AmountFormatter.format(bucket.outflow, settings)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TrendChart(
                    buckets = buckets,
                    granularity = granularity,
                    selectedDate = selectedDate,
                    displayUnit = displayUnit,
                    totalMovement = totalMovement,
                    onBucketSelect = { bucket -> onDateSelect(bucket.startDate.toEpochDay()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(246.dp),
                )
            }
        }
    }
}

@Composable
private fun CashFlowControlRow(
    mode: CashFlowCardMode,
    granularity: CashFlowGranularity,
    displayUnit: CashFlowDisplayUnit,
    onModeChange: (CashFlowCardMode) -> Unit,
    onGranularityChange: (CashFlowGranularity) -> Unit,
    onDisplayUnitChange: (CashFlowDisplayUnit) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleIconButton(
                selected = mode == CashFlowCardMode.CALENDAR,
                onClick = { onModeChange(CashFlowCardMode.CALENDAR) },
                icon = Icons.Outlined.CalendarMonth,
                contentDescription = "日历视图",
            )
            ToggleIconButton(
                selected = mode == CashFlowCardMode.TREND,
                onClick = { onModeChange(CashFlowCardMode.TREND) },
                icon = Icons.Outlined.BarChart,
                contentDescription = "趋势视图",
            )
        }

        SegmentedControl(
            modifier = Modifier.weight(1f),
            labels = CashFlowGranularity.entries.map { it.label },
            selectedIndex = CashFlowGranularity.entries.indexOf(granularity),
            onSelect = { index -> onGranularityChange(CashFlowGranularity.entries[index]) },
        )

        SegmentedControl(
            labels = CashFlowDisplayUnit.entries.map { it.label },
            selectedIndex = CashFlowDisplayUnit.entries.indexOf(displayUnit),
            onSelect = { index -> onDisplayUnitChange(CashFlowDisplayUnit.entries[index]) },
        )
    }
}

@Composable
private fun CalendarHeader(
    text: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIconButton(
            icon = Icons.Outlined.ChevronLeft,
            onClick = onPrevious,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        HeaderIconButton(
            icon = Icons.Outlined.ChevronRight,
            onClick = onNext,
        )
    }
}

@Composable
private fun CalendarContent(
    granularity: CashFlowGranularity,
    today: LocalDate,
    visibleDate: LocalDate,
    selectedDate: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    when (granularity) {
        CashFlowGranularity.DAY -> DayCalendarGrid(
            visibleMonth = YearMonth.from(visibleDate),
            selectedDate = selectedDate,
            today = today,
            totalsByDate = totalsByDate,
            totalMovement = totalMovement,
            displayUnit = displayUnit,
            settings = settings,
            onDateSelect = onDateSelect,
        )
        CashFlowGranularity.WEEK -> WeekCalendarGrid(
            visibleMonth = YearMonth.from(visibleDate),
            selectedDate = selectedDate,
            totalsByDate = totalsByDate,
            totalMovement = totalMovement,
            displayUnit = displayUnit,
            settings = settings,
            onDateSelect = onDateSelect,
        )
        CashFlowGranularity.MONTH -> MonthCalendarGrid(
            year = visibleDate.year,
            selectedDate = selectedDate,
            totalsByDate = totalsByDate,
            totalMovement = totalMovement,
            displayUnit = displayUnit,
            settings = settings,
            onDateSelect = onDateSelect,
        )
        CashFlowGranularity.YEAR -> YearCalendarGrid(
            visibleDate = visibleDate,
            selectedDate = selectedDate,
            totalsByDate = totalsByDate,
            totalMovement = totalMovement,
            displayUnit = displayUnit,
            settings = settings,
            onDateSelect = onDateSelect,
        )
    }
}

@Composable
private fun DayCalendarGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    val firstDay = visibleMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.toSundayIndex()
    val trailingBlanks = ((leadingBlanks + visibleMonth.lengthOfMonth() + 6) / 7) * 7 - leadingBlanks - visibleMonth.lengthOfMonth()
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        repeat(visibleMonth.lengthOfMonth()) { offset -> add(visibleMonth.atDay(offset + 1)) }
        repeat(trailingBlanks) { add(null) }
    }
    val maxAbs = cells.mapNotNull { date -> date?.let { totalsByDate[it]?.netAmount } }.maxOfOrNull { abs(it) } ?: 1L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val totals = totalsByDate[date] ?: BucketTotals()
                        val isSelected = date == selectedDate
                        val isFuture = date > today
                        val label = if (date == today) "今" else date.dayOfMonth.toString()
                        val detail = when {
                            totals.inflow == 0L && totals.outflow == 0L -> "暂无记录"
                            else -> formatCompactValue(totals.netAmount, totals, displayUnit, totalMovement, settings)
                        }
                        DayCell(
                            modifier = Modifier.weight(1f),
                            label = label,
                            value = detail,
                            isSelected = isSelected,
                            isFuture = isFuture,
                            color = bucketTextColor(totals.netAmount, maxAbs),
                            onClick = { onDateSelect(date.toEpochDay()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCalendarGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    val buckets = buildWeekBuckets(totalsByDate, visibleMonth)
    PeriodTileGrid(
        columns = 2,
        buckets = buckets,
        selectedDate = selectedDate,
        totalMovement = totalMovement,
        displayUnit = displayUnit,
        settings = settings,
        onDateSelect = onDateSelect,
    )
}

@Composable
private fun MonthCalendarGrid(
    year: Int,
    selectedDate: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    val buckets = (1..12).map { month ->
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        buildBucket(
            label = "${month}月",
            startDate = start,
            endDate = end,
            totalsByDate = totalsByDate,
        )
    }
    PeriodTileGrid(
        columns = 3,
        buckets = buckets,
        selectedDate = selectedDate,
        totalMovement = totalMovement,
        displayUnit = displayUnit,
        settings = settings,
        onDateSelect = onDateSelect,
    )
}

@Composable
private fun YearCalendarGrid(
    visibleDate: LocalDate,
    selectedDate: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    val startYear = ((visibleDate.year - 1) / 12) * 12 + 1
    val buckets = (startYear until startYear + 12).map { year ->
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        buildBucket(
            label = "${year}年",
            startDate = start,
            endDate = end,
            totalsByDate = totalsByDate,
        )
    }
    PeriodTileGrid(
        columns = 3,
        buckets = buckets,
        selectedDate = selectedDate,
        totalMovement = totalMovement,
        displayUnit = displayUnit,
        settings = settings,
        onDateSelect = onDateSelect,
    )
}

@Composable
private fun PeriodTileGrid(
    columns: Int,
    buckets: List<CashFlowBucket>,
    selectedDate: LocalDate,
    totalMovement: Long,
    displayUnit: CashFlowDisplayUnit,
    settings: AppSettings,
    onDateSelect: (Long) -> Unit,
) {
    val maxAbs = buckets.maxOfOrNull { abs(it.netAmount) } ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buckets.chunked(columns).forEach { rowBuckets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowBuckets.forEach { bucket ->
                    PeriodTile(
                        modifier = Modifier.weight(1f),
                        title = bucket.label,
                        value = formatCompactValue(bucket.netAmount, bucket.toTotals(), displayUnit, totalMovement, settings),
                        isSelected = selectedDate in bucket.startDate..bucket.endDate,
                        color = bucketTextColor(bucket.netAmount, maxAbs),
                        onClick = { onDateSelect(bucket.startDate.toEpochDay()) },
                    )
                }
                repeat(columns - rowBuckets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrendChart(
    buckets: List<CashFlowBucket>,
    granularity: CashFlowGranularity,
    selectedDate: LocalDate,
    displayUnit: CashFlowDisplayUnit,
    totalMovement: Long,
    onBucketSelect: (CashFlowBucket) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) {
        EmptyCashFlowBody()
        return
    }

    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val selectionLineColor = MaterialTheme.colorScheme.primary
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier = modifier) {
        val scrollState = rememberScrollState()
        val minWidth = (buckets.size * 42).dp
        val chartWidth = if (maxWidth > minWidth) maxWidth else minWidth
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(
                modifier = Modifier
                    .width(chartWidth)
                    .height(246.dp)
                    .pointerInput(buckets) {
                        detectTapGestures { offset ->
                            val leftPadding = 28.dp.toPx()
                            val chartAreaWidth = size.width - leftPadding - 8.dp.toPx()
                            if (offset.x < leftPadding || chartAreaWidth <= 0f) return@detectTapGestures
                            val stepX = chartAreaWidth / buckets.size.toFloat()
                            val index = ((offset.x - leftPadding) / stepX).toInt().coerceIn(0, buckets.lastIndex)
                            onBucketSelect(buckets[index])
                        }
                    },
            ) {
                val leftPadding = 28.dp.toPx()
                val rightPadding = 8.dp.toPx()
                val topPadding = 14.dp.toPx()
                val bottomPadding = 28.dp.toPx()
                val chartAreaWidth = size.width - leftPadding - rightPadding
                val chartAreaHeight = size.height - topPadding - bottomPadding
                val zeroY = topPadding + chartAreaHeight / 2f
                val maxValue = when (displayUnit) {
                    CashFlowDisplayUnit.AMOUNT -> {
                        buckets.maxOfOrNull { abs(it.netAmount) }?.coerceAtLeast(1L)?.toFloat() ?: 1f
                    }
                    CashFlowDisplayUnit.PERCENT -> {
                        buckets.maxOf {
                            abs(it.netAmount.toDouble() / totalMovement.toDouble() * 100.0)
                        }.coerceAtLeast(0.01).toFloat()
                    }
                }
                val stepX = chartAreaWidth / buckets.size.toFloat()
                val barWidth = stepX * 0.55f
                val selectedIndex = buckets.indexOfFirst { selectedDate in it.startDate..it.endDate }

                drawLine(
                    color = zeroLineColor,
                    start = Offset(leftPadding, zeroY),
                    end = Offset(size.width - rightPadding, zeroY),
                    strokeWidth = 1.dp.toPx(),
                )

                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                if (selectedIndex >= 0) {
                    val x = leftPadding + selectedIndex * stepX + stepX / 2f
                    drawLine(
                        color = selectionLineColor,
                        start = Offset(x, topPadding),
                        end = Offset(x, size.height - bottomPadding),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }

                buckets.forEachIndexed { index, bucket ->
                    val x = leftPadding + index * stepX + (stepX - barWidth) / 2f
                    val value = when (displayUnit) {
                        CashFlowDisplayUnit.AMOUNT -> bucket.netAmount.toFloat()
                        CashFlowDisplayUnit.PERCENT -> (bucket.netAmount.toDouble() / totalMovement.toDouble() * 100.0).toFloat()
                    }
                    val barHeight = (abs(value) / maxValue) * (chartAreaHeight / 2f)
                    val color = if (value >= 0f) InflowRed else OutflowGreen
                    if (value >= 0f) {
                        drawRect(
                            color = color,
                            topLeft = Offset(x, zeroY - barHeight),
                            size = Size(barWidth, barHeight),
                        )
                    } else {
                        drawRect(
                            color = color,
                            topLeft = Offset(x, zeroY),
                            size = Size(barWidth, barHeight),
                        )
                    }

                    if (buckets.size <= 12 || index % (buckets.size / 6 + 1) == 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            trendAxisLabel(bucket, granularity),
                            x + barWidth / 2f,
                            size.height - 6.dp.toPx(),
                            android.graphics.Paint().apply {
                                this.color = axisTextColor.toArgb()
                                textSize = 10.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCashFlowBody() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "当前范围没有可绘制的现金流数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "补充记录后，日历和趋势会共用同一份现金流数据自动刷新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isSelected: Boolean,
    isFuture: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val titleColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
    val valueColor = if (isSelected) Color.White else if (isFuture || value == "暂无记录") NeutralGray else color
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PeriodTile(
    title: String,
    value: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) Color.White else color,
            )
        }
    }
}

@Composable
private fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .background(
                        color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedIndex == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToggleIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private data class BucketTotals(
    val inflow: Long = 0L,
    val outflow: Long = 0L,
) {
    val netAmount: Long
        get() = inflow - outflow
}

private data class CashFlowBucket(
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val inflow: Long,
    val outflow: Long,
) {
    val netAmount: Long
        get() = inflow - outflow

    fun toTotals(): BucketTotals = BucketTotals(inflow = inflow, outflow = outflow)
}

private fun buildDailyTotals(
    events: List<CashFlowEvent>,
    zoneId: ZoneId,
): Map<LocalDate, BucketTotals> {
    return events.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() }
        .mapValues { (_, dailyEvents) ->
            BucketTotals(
                inflow = dailyEvents.sumOf { it.inflow },
                outflow = dailyEvents.sumOf { it.outflow },
            )
        }
}

private fun buildBuckets(
    totalsByDate: Map<LocalDate, BucketTotals>,
    granularity: CashFlowGranularity,
    visibleDate: LocalDate,
): List<CashFlowBucket> {
    return when (granularity) {
        CashFlowGranularity.DAY -> {
            val month = YearMonth.from(visibleDate)
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                buildBucket(
                    label = day.toString(),
                    startDate = date,
                    endDate = date,
                    totalsByDate = totalsByDate,
                )
            }
        }
        CashFlowGranularity.WEEK -> buildWeekBuckets(totalsByDate, YearMonth.from(visibleDate))
        CashFlowGranularity.MONTH -> {
            (1..12).map { month ->
                val start = LocalDate.of(visibleDate.year, month, 1)
                val end = start.withDayOfMonth(start.lengthOfMonth())
                buildBucket(
                    label = "${month}月",
                    startDate = start,
                    endDate = end,
                    totalsByDate = totalsByDate,
                )
            }
        }
        CashFlowGranularity.YEAR -> {
            val startYear = ((visibleDate.year - 1) / 12) * 12 + 1
            (startYear until startYear + 12).map { year ->
                buildBucket(
                    label = "${year}年",
                    startDate = LocalDate.of(year, 1, 1),
                    endDate = LocalDate.of(year, 12, 31),
                    totalsByDate = totalsByDate,
                )
            }
        }
    }
}

private fun buildWeekBuckets(
    totalsByDate: Map<LocalDate, BucketTotals>,
    visibleMonth: YearMonth,
): List<CashFlowBucket> {
    val monthStart = visibleMonth.atDay(1)
    val monthEnd = visibleMonth.atEndOfMonth()
    var cursor = monthStart.minusDays(monthStart.dayOfWeek.toSundayIndex().toLong())
    val lastVisibleDay = monthEnd.plusDays((6 - monthEnd.dayOfWeek.toSundayIndex()).toLong())
    val buckets = mutableListOf<CashFlowBucket>()
    while (!cursor.isAfter(lastVisibleDay)) {
        val end = cursor.plusDays(6)
        buckets += buildBucket(
            label = "${cursor.monthValue}/${cursor.dayOfMonth}-${end.monthValue}/${end.dayOfMonth}",
            startDate = cursor,
            endDate = end,
            totalsByDate = totalsByDate,
        )
        cursor = cursor.plusWeeks(1)
    }
    return buckets
}

private fun buildBucket(
    label: String,
    startDate: LocalDate,
    endDate: LocalDate,
    totalsByDate: Map<LocalDate, BucketTotals>,
): CashFlowBucket {
    var inflow = 0L
    var outflow = 0L
    var cursor = startDate
    while (!cursor.isAfter(endDate)) {
        val totals = totalsByDate[cursor] ?: BucketTotals()
        inflow += totals.inflow
        outflow += totals.outflow
        cursor = cursor.plusDays(1)
    }
    return CashFlowBucket(
        label = label,
        startDate = startDate,
        endDate = endDate,
        inflow = inflow,
        outflow = outflow,
    )
}

private fun headerText(
    visibleDate: LocalDate,
    granularity: CashFlowGranularity,
): String {
    return when (granularity) {
        CashFlowGranularity.DAY,
        CashFlowGranularity.WEEK,
        -> "${visibleDate.year}年${visibleDate.monthValue}月"
        CashFlowGranularity.MONTH -> "${visibleDate.year}年"
        CashFlowGranularity.YEAR -> {
            val startYear = ((visibleDate.year - 1) / 12) * 12 + 1
            "${startYear}-${startYear + 11}"
        }
    }
}

private fun bucketHeadline(
    bucket: CashFlowBucket,
    granularity: CashFlowGranularity,
): String {
    return when (granularity) {
        CashFlowGranularity.DAY -> "${bucket.startDate.year}年${bucket.startDate.monthValue}月${bucket.startDate.dayOfMonth}日"
        CashFlowGranularity.WEEK -> "${bucket.startDate.monthValue}月${bucket.startDate.dayOfMonth}日 - ${bucket.endDate.monthValue}月${bucket.endDate.dayOfMonth}日"
        CashFlowGranularity.MONTH -> "${bucket.startDate.year}年${bucket.startDate.monthValue}月"
        CashFlowGranularity.YEAR -> "${bucket.startDate.year}年"
    }
}

private fun trendAxisLabel(
    bucket: CashFlowBucket,
    granularity: CashFlowGranularity,
): String {
    return when (granularity) {
        CashFlowGranularity.DAY -> bucket.startDate.dayOfMonth.toString()
        CashFlowGranularity.WEEK -> bucket.startDate.dayOfMonth.toString()
        CashFlowGranularity.MONTH -> bucket.startDate.monthValue.toString()
        CashFlowGranularity.YEAR -> bucket.startDate.year.toString()
    }
}

private fun buildMonthStatus(
    totalsByDate: Map<LocalDate, BucketTotals>,
    visibleMonth: YearMonth,
    today: LocalDate,
    settings: AppSettings,
): String? {
    val monthDays = (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    val monthNet = monthDays.sumOf { totalsByDate[it]?.netAmount ?: 0L }
    val currentMonth = YearMonth.from(today)
    if (visibleMonth == currentMonth) {
        var streak = 0
        var cursor = today
        while (cursor.month == visibleMonth.month && cursor.year == visibleMonth.year) {
            if ((totalsByDate[cursor]?.netAmount ?: 0L) > 0L) {
                streak += 1
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }
        if (streak >= 3) {
            return "你本月已连续${streak}天净流入"
        }
    }
    return if (monthNet >= 0L) {
        "本月净流入 ${AmountFormatter.format(monthNet, settings)}"
    } else {
        "本月净流出 ${AmountFormatter.format(abs(monthNet), settings)}"
    }
}

private fun formatBucketPrimary(
    bucket: CashFlowBucket,
    unit: CashFlowDisplayUnit,
    totalMovement: Long,
    settings: AppSettings,
): String {
    return formatCompactValue(bucket.netAmount, bucket.toTotals(), unit, totalMovement, settings)
}

private fun formatCompactValue(
    netAmount: Long,
    totals: BucketTotals,
    unit: CashFlowDisplayUnit,
    totalMovement: Long,
    settings: AppSettings,
): String {
    return when (unit) {
        CashFlowDisplayUnit.AMOUNT -> AmountFormatter.format(netAmount, settings)
        CashFlowDisplayUnit.PERCENT -> {
            val ratio = when {
                totalMovement <= 0L -> 0.0
                totals.inflow + totals.outflow > 0L -> netAmount.toDouble() / totalMovement.toDouble()
                else -> 0.0
            }
            "${if (ratio > 0) "+" else ""}${"%.1f".format(ratio * 100)}%"
        }
    }
}

private fun bucketTextColor(
    netAmount: Long,
    maxAbs: Long,
): Color {
    val base = when {
        netAmount > 0L -> InflowRed
        netAmount < 0L -> OutflowGreen
        else -> NeutralGray
    }
    if (netAmount == 0L) return base
    val alpha = 0.4f + 0.6f * (abs(netAmount).toFloat() / max(maxAbs, 1L).toFloat())
    return base.copy(alpha = alpha.coerceIn(0.4f, 1f))
}

private fun bucketPrimaryColor(
    netAmount: Long,
): Color {
    return when {
        netAmount > 0L -> InflowRed
        netAmount < 0L -> OutflowGreen
        else -> NeutralGray
    }
}

private fun DayOfWeek.toSundayIndex(): Int {
    return when (this) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }
}
