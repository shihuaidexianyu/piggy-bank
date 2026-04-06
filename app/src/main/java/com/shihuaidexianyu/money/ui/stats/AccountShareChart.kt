package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.usecase.AccountShare
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlin.math.abs

private val accountColorPalette = listOf(
    Color(0xFF6750A4),
    Color(0xFF2196F3),
    Color(0xFFFF9800),
    Color(0xFF4CAF50),
    Color(0xFFE91E63),
    Color(0xFF00BCD4),
    Color(0xFF9C27B0),
    Color(0xFFFF5722),
    Color(0xFF3F51B5),
    Color(0xFFCDDC39),
    Color(0xFF795548),
    Color(0xFF607D8B),
    Color(0xFFF44336),
    Color(0xFF009688),
    Color(0xFFFFC107),
)

@Composable
fun AccountShareChart(
    shares: List<AccountShare>,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    if (shares.isEmpty()) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val totalBalance = shares.sumOf { abs(it.balance) }
    if (totalBalance == 0L) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 36.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                shares.forEachIndexed { index, share ->
                    val sweep = (abs(share.balance).toFloat() / totalBalance.toFloat()) * 360f
                    val color = accountColorPalette[index % accountColorPalette.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
            Text(
                text = AmountFormatter.format(shares.sumOf { it.balance }, settings),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shares.forEachIndexed { index, share ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(
                                color = accountColorPalette[index % accountColorPalette.size],
                            )
                        }
                        Text(
                            text = share.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = AmountFormatter.format(share.balance, settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
