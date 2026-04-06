package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.usecase.NetAssetPoint
import kotlin.math.max
import kotlin.math.min

@Composable
fun NetAssetChart(
    points: List<NetAssetPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF2196F3),
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        if (points.size < 2) return@Canvas

        val leftPadding = 52.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val chartWidth = size.width - leftPadding
        val chartHeight = size.height - bottomPadding

        val values = points.map { it.totalBalance.toFloat() / 100f }
        val dataMax = values.max()
        val dataMin = values.min()
        val maxVal = max(dataMax, dataMin + 1f)
        val minVal = min(dataMin, maxVal - 1f)
        val range = maxVal - minVal

        // Y-axis grid lines and labels
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
            val value = minVal + range * fraction

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

        // Line path
        val stepX = chartWidth / (points.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = leftPadding + index * stepX
            val y = chartHeight - ((value - minVal) / range) * chartHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx()),
        )

        // Dots
        values.forEachIndexed { index, value ->
            val x = leftPadding + index * stepX
            val y = chartHeight - ((value - minVal) / range) * chartHeight
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        // X-axis labels
        points.forEachIndexed { index, point ->
            if (points.size <= 12 || index % (points.size / 7 + 1) == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    point.label,
                    leftPadding + index * stepX,
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
