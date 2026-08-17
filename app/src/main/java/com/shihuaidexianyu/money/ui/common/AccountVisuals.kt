package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.CurrencyYuan
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.theme.LocalDarkTheme
import com.shihuaidexianyu.money.domain.model.ACCOUNT_GEOMETRY_NAMES
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
    AccountIconOption(
        name = name,
        labelRes = accountIconLabelRes(name),
        icon = accountIconVector(name),
    )
}

/**
 * The semantic icon backing any stored icon name. Names from the retired geometry catalog
 * ([ACCOUNT_GEOMETRY_NAMES]) — still present in old accounts and backups — resolve to a fixed
 * semantic slot, spread across the catalog so a geo-era account list stays visually varied.
 */
internal fun semanticIconName(name: String): String {
    val normalized = normalizeAccountIconName(name)
    return when (normalized) {
        "geo_rings" -> "wallet"
        "geo_pie" -> "pie_chart"
        "geo_hexagon" -> "bank"
        "geo_arc" -> "investment"
        "geo_dots" -> "cash"
        "geo_lines" -> "chart"
        "geo_sun" -> "savings"
        "geo_wave" -> "currency_exchange"
        "geo_diamond" -> "credit_card"
        "geo_grid" -> "home"
        else -> normalized
    }
}

@StringRes
fun accountIconLabelRes(name: String): Int {
    return when (semanticIconName(name)) {
        "wallet" -> R.string.account_icon_wallet
        "cash" -> R.string.account_icon_cash
        "bank" -> R.string.account_icon_bank
        "credit_card" -> R.string.account_icon_credit_card
        "savings" -> R.string.account_icon_savings
        "qr_code" -> R.string.account_icon_qr_code
        "wechat" -> R.string.account_icon_wechat
        "alipay" -> R.string.account_icon_alipay
        "receipt" -> R.string.account_icon_receipt
        "investment" -> R.string.account_icon_investment
        "chart" -> R.string.account_icon_chart
        "pie_chart" -> R.string.account_icon_pie_chart
        "currency" -> R.string.account_icon_currency
        "currency_exchange" -> R.string.account_icon_currency_exchange
        "stock" -> R.string.account_icon_stock
        "insurance" -> R.string.account_icon_insurance
        "home" -> R.string.account_icon_home
        "car" -> R.string.account_icon_car
        "subway" -> R.string.account_icon_subway
        "flight" -> R.string.account_icon_flight
        "restaurant" -> R.string.account_icon_restaurant
        "shopping" -> R.string.account_icon_shopping
        "utilities" -> R.string.account_icon_utilities
        "entertainment" -> R.string.account_icon_entertainment
        "medical" -> R.string.account_icon_medical
        "school" -> R.string.account_icon_school
        "fitness" -> R.string.account_icon_fitness
        "pets" -> R.string.account_icon_pets
        "child_care" -> R.string.account_icon_child_care
        "gift" -> R.string.account_icon_gift
        "phone" -> R.string.account_icon_phone
        else -> R.string.account_icon_work
    }
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

@Composable
fun accountColorLabel(name: String): String = stringResource(accountColorLabelRes(name))

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
    /**
     * When non-null, draws a proportion ring around the badge: the arc length is this fraction
     * (0..1) of the full circle, turning the account's share of total assets into a graphic.
     * Decorative — callers announce the share through text semantics.
     */
    shareFraction: Float? = null,
) {
    val accent = if (isClosed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        accountVisualColor(colorName)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (shareFraction != null) {
            val trackColor = accent.copy(alpha = 0.16f)
            val sweep = shareFraction.coerceIn(0f, 1f) * 360f
            Canvas(modifier = Modifier.size(size + ShareRingGap * 2 + ShareRingStroke)) {
                val strokePx = ShareRingStroke.toPx()
                val arcTopLeft = Offset(strokePx / 2f, strokePx / 2f)
                val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
                drawCircle(
                    color = trackColor,
                    radius = (this.size.minDimension - strokePx) / 2f,
                    style = Stroke(strokePx),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(strokePx, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.size(size),
            color = accent.copy(alpha = if (isClosed) 0.10f else 0.12f),
            shape = CircleShape,
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the enclosing row merges its own semantics, and the picker
                // announces each icon through its label.
                Icon(
                    imageVector = accountIconVector(iconName),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Both palettes were validated with the dataviz six-checks validator against the app's real
 * surfaces (light: #FFFFFF cards; dark: a #161616 canvas with #242424 cards) in the
 * exact order of [ACCOUNT_COLOR_NAMES] — every value clears the lightness band, chroma floor,
 * adjacent-pair CVD separation, and the 3:1 contrast floor. Change a hex or the order only
 * together with a re-run of the validator.
 */
@Composable
internal fun accountVisualColor(name: String): Color {
    val isDark = LocalDarkTheme.current
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

private fun accountIconVector(name: String): ImageVector = when (semanticIconName(name)) {
    "wallet" -> Icons.Rounded.AccountBalanceWallet
    "cash" -> Icons.Rounded.Payments
    "bank" -> Icons.Rounded.AccountBalance
    "credit_card" -> Icons.Rounded.CreditCard
    "savings" -> Icons.Rounded.Savings
    "qr_code" -> Icons.Rounded.QrCode2
    "wechat" -> WeChatIcon
    "alipay" -> AlipayIcon
    "receipt" -> Icons.Rounded.ReceiptLong
    "investment" -> Icons.Rounded.TrendingUp
    "chart" -> Icons.Rounded.BarChart
    "pie_chart" -> Icons.Rounded.PieChart
    "currency" -> Icons.Rounded.CurrencyYuan
    "currency_exchange" -> Icons.Rounded.CurrencyExchange
    "stock" -> Icons.Rounded.CandlestickChart
    "insurance" -> Icons.Rounded.HealthAndSafety
    "home" -> Icons.Rounded.Home
    "car" -> Icons.Rounded.DirectionsCar
    "subway" -> Icons.Rounded.DirectionsSubway
    "flight" -> Icons.Rounded.Flight
    "restaurant" -> Icons.Rounded.Restaurant
    "shopping" -> Icons.Rounded.ShoppingCart
    "utilities" -> Icons.Rounded.Bolt
    "entertainment" -> Icons.Rounded.Movie
    "medical" -> Icons.Rounded.MedicalServices
    "school" -> Icons.Rounded.School
    "fitness" -> Icons.Rounded.FitnessCenter
    "pets" -> Icons.Rounded.Pets
    "child_care" -> Icons.Rounded.ChildCare
    "gift" -> Icons.Rounded.CardGiftcard
    "phone" -> Icons.Rounded.Smartphone
    else -> Icons.Rounded.Work
}

private val ShareRingStroke = 2.5.dp
private val ShareRingGap = 2.dp
