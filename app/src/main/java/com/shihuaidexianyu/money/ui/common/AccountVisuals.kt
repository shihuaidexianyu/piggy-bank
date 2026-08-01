package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        "purple" -> R.string.account_color_purple
        "gold" -> R.string.account_color_gold
        "orange" -> R.string.account_color_orange
        "teal" -> R.string.account_color_teal
        "red" -> R.string.account_color_red
        "cyan" -> R.string.account_color_cyan
        "rose" -> R.string.account_color_rose
        "indigo" -> R.string.account_color_indigo
        "brown" -> R.string.account_color_brown
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
        "chart" -> R.string.account_icon_chart
        "currency" -> R.string.account_icon_currency
        "home" -> R.string.account_icon_home
        "phone" -> R.string.account_icon_phone
        "shopping" -> R.string.account_icon_shopping
        "restaurant" -> R.string.account_icon_restaurant
        "car" -> R.string.account_icon_car
        "flight" -> R.string.account_icon_flight
        "gift" -> R.string.account_icon_gift
        "school" -> R.string.account_icon_school
        "medical" -> R.string.account_icon_medical
        "pets" -> R.string.account_icon_pets
        "work" -> R.string.account_icon_work
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
    Surface(
        modifier = modifier.size(size),
        color = accountVisualColor(colorName),
        shape = CircleShape,
        content = {},
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
    Surface(
        modifier = modifier.size(size),
        color = accent.copy(alpha = if (isClosed) 0.10f else 0.12f),
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.size(size),
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
}

/**
 * Both palettes were validated with the dataviz six-checks validator against the app's real
 * surfaces (light: #FFFFFF, dark: #201C18) in the exact order of [ACCOUNT_COLOR_NAMES] — every
 * value clears the lightness band, chroma floor, adjacent-pair CVD separation, and the 3:1
 * contrast floor. Change a hex or the order only together with a re-run of the validator.
 */
@Composable
internal fun accountVisualColor(name: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (normalizeAccountColorName(name)) {
        "green" -> if (isDark) Color(0xFF43A047) else Color(0xFF2E7D32)
        "purple" -> if (isDark) Color(0xFF9575CD) else Color(0xFF7E57C2)
        "gold" -> if (isDark) Color(0xFFB8821A) else Color(0xFFA16207)
        "orange" -> if (isDark) Color(0xFFD66F1B) else Color(0xFFE87124)
        "teal" -> if (isDark) Color(0xFF26A69A) else Color(0xFF00897B)
        "red" -> if (isDark) Color(0xFFE15451) else Color(0xFFC62828)
        "cyan" -> if (isDark) Color(0xFF2193B3) else Color(0xFF0891B2)
        "rose" -> if (isDark) Color(0xFFE0447C) else Color(0xFFC2185B)
        "indigo" -> if (isDark) Color(0xFF7986F8) else Color(0xFF4F46E5)
        "brown" -> if (isDark) Color(0xFFB0682A) else Color(0xFF92400E)
        "gray" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (isDark) Color(0xFF5B8DEF) else Color(0xFF2563EB)
    }
}

private fun accountIconVector(name: String): ImageVector {
    return when (normalizeAccountIconName(name)) {
        "bank" -> Icons.Rounded.AccountBalance
        "cash" -> Icons.Rounded.Payments
        "credit_card" -> Icons.Rounded.CreditCard
        "savings" -> Icons.Rounded.Savings
        "investment" -> Icons.AutoMirrored.Rounded.TrendingUp
        "chart" -> Icons.AutoMirrored.Rounded.ShowChart
        "currency" -> Icons.Rounded.CurrencyExchange
        "home" -> Icons.Rounded.Home
        "phone" -> Icons.Rounded.Smartphone
        "shopping" -> Icons.Rounded.ShoppingBag
        "restaurant" -> Icons.Rounded.Restaurant
        "car" -> Icons.Rounded.DirectionsCar
        "flight" -> Icons.Rounded.Flight
        "gift" -> Icons.Rounded.CardGiftcard
        "school" -> Icons.Rounded.School
        "medical" -> Icons.Rounded.MedicalServices
        "pets" -> Icons.Rounded.Pets
        "work" -> Icons.Rounded.BusinessCenter
        else -> Icons.Rounded.AccountBalanceWallet
    }
}
