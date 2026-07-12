package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.ACCOUNT_ICON_NAMES
import com.shihuaidexianyu.money.domain.model.ACCOUNT_COLOR_NAMES
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName

data class AccountVisualOption(
    val name: String,
    @param:StringRes val labelRes: Int,
)

data class AccountIconOption(
    val name: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

val AccountColorOptions = ACCOUNT_COLOR_NAMES.map { name ->
    AccountVisualOption(name = name, labelRes = accountColorLabelRes(name))
}

val AccountIconOptions = ACCOUNT_ICON_NAMES.map { name ->
    AccountIconOption(name = name, labelRes = accountIconLabelRes(name), icon = accountIconVector(name))
}

@StringRes
fun accountColorLabelRes(name: String): Int {
    return when (normalizeAccountColorName(name)) {
        "green" -> R.string.account_color_green
        "orange" -> R.string.account_color_orange
        "purple" -> R.string.account_color_purple
        "red" -> R.string.account_color_red
        "teal" -> R.string.account_color_teal
        "gray" -> R.string.account_color_gray
        else -> R.string.account_color_blue
    }
}

@StringRes
fun accountIconLabelRes(name: String): Int {
    return when (normalizeAccountIconName(name)) {
        "bank" -> R.string.account_icon_bank
        "cash" -> R.string.account_icon_cash
        "credit_card" -> R.string.account_icon_credit_card
        "savings" -> R.string.account_icon_savings
        "investment" -> R.string.account_icon_investment
        "home" -> R.string.account_icon_home
        "phone" -> R.string.account_icon_phone
        "shopping" -> R.string.account_icon_shopping
        else -> R.string.account_icon_wallet
    }
}

@Composable
fun accountColorLabel(name: String): String = stringResource(accountColorLabelRes(name))

@Composable
fun accountIconLabel(name: String): String = stringResource(accountIconLabelRes(name))

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
    isClosed: Boolean = false,
) {
    val accent = if (isClosed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        accountVisualColor(colorName)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = accent.copy(alpha = if (isClosed) 0.10f else 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = accountIconVector(iconName),
            contentDescription = stringResource(accountIconLabelRes(iconName)),
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun accountVisualColor(name: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (normalizeAccountColorName(name)) {
        "green" -> if (isDark) Color(0xFF6EA67A) else Color(0xFF2E7D32)
        "orange" -> if (isDark) Color(0xFFEC9F5A) else Color(0xFFE87124)
        "purple" -> if (isDark) Color(0xFFA48BD0) else Color(0xFF7E57C2)
        "red" -> if (isDark) Color(0xFFD8726A) else Color(0xFFC62828)
        "teal" -> if (isDark) Color(0xFF4DB0A4) else Color(0xFF00897B)
        "gray" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (isDark) Color(0xFF6691E8) else Color(0xFF2563EB)
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
