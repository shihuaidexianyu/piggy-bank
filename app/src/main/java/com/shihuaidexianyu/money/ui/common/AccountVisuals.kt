package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.ACCOUNT_COLOR_NAMES
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName

data class AccountVisualOption(
    val name: String,
    val label: String,
)

val AccountColorOptions = ACCOUNT_COLOR_NAMES.map { name ->
    AccountVisualOption(name = name, label = accountColorLabel(name))
}

fun accountColorLabel(name: String): String {
    return when (normalizeAccountColorName(name)) {
        "green" -> "绿色"
        "orange" -> "橙色"
        "purple" -> "紫色"
        "red" -> "红色"
        "teal" -> "青色"
        "gray" -> "灰色"
        else -> "蓝色"
    }
}

@Composable
fun AccountColorSwatch(
    colorName: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = accountVisualColor(colorName), shape = CircleShape),
    )
}

@Composable
private fun accountVisualColor(name: String): Color {
    return when (normalizeAccountColorName(name)) {
        "green" -> Color(0xFF2E7D32)
        "orange" -> Color(0xFFE87124)
        "purple" -> Color(0xFF7E57C2)
        "red" -> Color(0xFFC62828)
        "teal" -> Color(0xFF00897B)
        "gray" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color(0xFF2563EB)
    }
}
