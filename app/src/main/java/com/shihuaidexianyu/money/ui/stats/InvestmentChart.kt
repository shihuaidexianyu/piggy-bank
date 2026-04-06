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
import com.shihuaidexianyu.money.domain.usecase.InvestmentPoint
import kotlin.math.abs
import kotlin.math.max

@Composable
fun InvestmentChart(
    points: List<InvestmentPoint>,
    modifier: Modifier = Modifier,
    gainColor: Color = Color(0xFF4CAF50),
    lossColor: Color = Color(0xFFE53935),
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas

        val leftPadding = 52.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - bottomPadding

        val maxAbs = max(points.maxOf { abs(it.pnl) }.toFloat() / 100f, 0.01f)
        val zeroY = chartHeight / 2f

        // Y-axis grid lines and labels
        val tickCount = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val labelPaint = android.graphics.Paint().apply {
            this.color = textColor.toArgb()
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (i in 0..tickCount) {
            val fraction = i.toFloat() / tickCount
            val y = chartHeight - fraction * chartHeight
            val value = -maxAbs + 2 * maxAbs * fraction

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

        // Zero line (solid)
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
        )

        // Bars
        val barSpacing = chartWidth / points.size
        val barWidth = barSpacing * 0.7f

        points.forEachIndexed { index, point ->
            val x = leftPadding + index * barSpacing + (barSpacing - barWidth) / 2
            val pnlValue = point.pnl.toFloat() / 100f
            val barH = (abs(pnlValue) / maxAbs) * (chartHeight / 2f)
            val barColor = if (point.pnl >= 0) gainColor else lossColor

            if (point.pnl >= 0) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, zeroY - barH),
                    size = Size(barWidth, barH),
                )
            } else {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, zeroY),
                    size = Size(barWidth, barH),
                )
            }

            // X-axis label
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                leftPadding + index * barSpacing + barSpacing / 2,
                size.height - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = textColor.toArgb()
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                },
            )
        }
    }
}
