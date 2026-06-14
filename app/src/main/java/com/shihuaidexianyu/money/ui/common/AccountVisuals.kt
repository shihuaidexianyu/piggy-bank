package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.ACCOUNT_ICON_NAMES
import com.shihuaidexianyu.money.domain.model.ACCOUNT_COLOR_NAMES
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName

data class AccountVisualOption(
    val name: String,
    val label: String,
)

data class AccountIconOption(
    val name: String,
    val label: String,
    val icon: ImageVector,
)

val AccountColorOptions = ACCOUNT_COLOR_NAMES.map { name ->
    AccountVisualOption(name = name, label = accountColorLabel(name))
}

val AccountIconOptions = ACCOUNT_ICON_NAMES.map { name ->
    AccountIconOption(name = name, label = accountIconLabel(name), icon = accountIconVector(name))
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

fun accountIconLabel(name: String): String {
    return when (normalizeAccountIconName(name)) {
        "bank" -> "银行"
        "cash" -> "现金"
        "credit_card" -> "信用卡"
        "savings" -> "储蓄"
        "investment" -> "理财"
        "home" -> "房产"
        "phone" -> "手机"
        "shopping" -> "消费"
        else -> "钱包"
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
fun AccountIconBadge(
    iconName: String,
    colorName: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 24.dp,
    isArchived: Boolean = false,
) {
    val accent = if (isArchived) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        accountVisualColor(colorName)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = accent.copy(alpha = if (isArchived) 0.10f else 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = accountIconVector(iconName),
            contentDescription = accountIconLabel(iconName),
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun accountVisualColor(name: String): Color {
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

private fun accountIconVector(name: String): ImageVector {
    return when (normalizeAccountIconName(name)) {
        "bank" -> Icons.Rounded.AccountBalance
        "cash" -> Icons.Rounded.Payments
        "credit_card" -> Icons.Rounded.CreditCard
        "savings" -> Icons.Rounded.Savings
        "investment" -> Icons.AutoMirrored.Rounded.TrendingUp
        "home" -> Icons.Rounded.Home
        "phone" -> Icons.Rounded.Smartphone
        "shopping" -> Icons.Rounded.ShoppingBag
        else -> Icons.Rounded.AccountBalanceWallet
    }
}
