package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.usecase.CashFlowBar
import kotlin.math.max

@Composable
fun CashFlowChart(
    bars: List<CashFlowBar>,
    modifier: Modifier = Modifier,
    inflowColor: Color = Color(0xFF4CAF50),
    outflowColor: Color = Color(0xFFE53935),
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        if (bars.isEmpty()) return@Canvas

        val leftPadding = 52.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - bottomPadding

        val maxValue = max(
            bars.maxOf { max(it.inflow, it.outflow) }.toFloat() / 100f,
            0.01f,
        )

        // Y-axis grid lines and labels (4 ticks)
        val tickCount = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val labelPaint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (i in 0..tickCount) {
            val fraction = i.toFloat() / tickCount
            val y = chartHeight - fraction * chartHeight
            val value = maxValue * fraction

            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width, y),
                pathEffect = dashEffect,
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatAxisValue(value),
                leftPadding - 6.dp.toPx(),
                y + 4.dp.toPx(),
                labelPaint,
            )
        }

        // Bars
        val barGroupWidth = chartWidth / bars.size
        val barWidth = barGroupWidth * 0.35f

        bars.forEachIndexed { index, bar ->
            val groupX = leftPadding + index * barGroupWidth

            val inflowH = (bar.inflow.toFloat() / 100f / maxValue) * chartHeight
            drawRect(
                color = inflowColor,
                topLeft = Offset(groupX + barGroupWidth * 0.08f, chartHeight - inflowH),
                size = Size(barWidth, inflowH),
            )

            val outflowH = (bar.outflow.toFloat() / 100f / maxValue) * chartHeight
            drawRect(
                color = outflowColor,
                topLeft = Offset(groupX + barGroupWidth * 0.08f + barWidth + 2.dp.toPx(), chartHeight - outflowH),
                size = Size(barWidth, outflowH),
            )

            // X-axis label
            if (bars.size <= 12 || index % (bars.size / 7 + 1) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    groupX + barGroupWidth / 2,
                    size.height - 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = textColor.toArgb()
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    },
                )
            }
        }
    }
}
