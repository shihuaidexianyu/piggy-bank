package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import kotlin.math.cos
import kotlin.math.sin

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

val AccountIconOptions = ACCOUNT_GEOMETRY_NAMES.mapIndexed { index, name ->
    AccountIconOption(
        name = name,
        labelRes = accountPatternLabelRes(index),
        icon = accountGeometryVector(index),
    )
}

@StringRes
private fun accountPatternLabelRes(index: Int): Int = when (index % ACCOUNT_GEOMETRY_NAMES.size) {
    0 -> R.string.account_pattern_1
    1 -> R.string.account_pattern_2
    2 -> R.string.account_pattern_3
    3 -> R.string.account_pattern_4
    4 -> R.string.account_pattern_5
    5 -> R.string.account_pattern_6
    6 -> R.string.account_pattern_7
    7 -> R.string.account_pattern_8
    8 -> R.string.account_pattern_9
    else -> R.string.account_pattern_10
}

/**
 * Stable pattern slot for any stored icon name. New picks use the geometry catalog directly;
 * legacy names from older backups map to a fixed slot by their position in the legacy list, so
 * existing accounts keep a varied, deterministic pattern without any data migration.
 */
internal fun accountPatternIndex(name: String): Int {
    val normalized = normalizeAccountIconName(name)
    val geometryIndex = ACCOUNT_GEOMETRY_NAMES.indexOf(normalized)
    if (geometryIndex >= 0) return geometryIndex
    val legacyIndex = ACCOUNT_ICON_NAMES.indexOf(normalized)
    return if (legacyIndex >= 0) legacyIndex % ACCOUNT_GEOMETRY_NAMES.size else 0
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
            // Decorative geometry: the enclosing row merges its own semantics, and the picker
            // dialogs announce each pattern through their labels — reading "图案 N" here was
            // pure noise for screen-reader users.
            Icon(
                imageVector = accountIconVector(iconName),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Both palettes were validated with the dataviz six-checks validator against the app's real
 * surfaces (light: #FFFFFF cards; dark: a pure-black #000000 canvas with #141414 cards) in the
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

private fun accountIconVector(name: String): ImageVector =
    accountGeometryVector(accountPatternIndex(name))

private const val GeometryStrokeWidth = 2f

/**
 * Procedurally drawn geometric glyphs in a 24x24 viewport. Each pattern is a distinct silhouette
 * (rings, pie, hexagon, arc, dots, stripes, sun, waves, diamond, grid) so accounts read as
 * unique without any semantic icon. Single-color paths — the Icon tint supplies the account hue.
 */
private fun accountGeometryVector(patternIndex: Int): ImageVector {
    val builder = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    when (patternIndex % ACCOUNT_GEOMETRY_NAMES.size) {
        0 -> { // Concentric rings
            builder.strokedCircle(12f, 12f, 8f)
            builder.strokedCircle(12f, 12f, 4.5f)
            builder.filledCircle(12f, 12f, 1.5f)
        }
        1 -> { // Three-quarter pie
            builder.path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                lineTo(20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 12f, 4f)
                close()
            }
            builder.filledCircle(20f, 12f, 1.5f)
        }
        2 -> { // Hexagon with center dot
            builder.polygon(
                regularPolygon(12f, 12f, 8.5f, sides = 6, startAngleDegrees = -90.0),
                filled = false,
            )
            builder.filledCircle(12f, 12f, 2f)
        }
        3 -> { // Arc with endpoint dot
            builder.path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = GeometryStrokeWidth,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 8f, 5.07f)
            }
            builder.filledCircle(8f, 5.07f, 1.8f)
        }
        4 -> { // Dot matrix
            listOf(
                8f to 6f, 16f to 6f,
                8f to 12f, 16f to 12f,
                8f to 18f, 16f to 18f,
            ).forEach { (x, y) -> builder.filledCircle(x, y, 2f) }
        }
        5 -> { // Diagonal stripes
            builder.line(5f, 18f, 10f, 8f)
            builder.line(9f, 18f, 14f, 8f)
            builder.line(13f, 18f, 18f, 8f)
        }
        6 -> { // Sun
            builder.filledCircle(12f, 12f, 5f)
            repeat(8) { i ->
                val angle = Math.toRadians(i * 45.0)
                val rayCos = cos(angle).toFloat()
                val raySin = sin(angle).toFloat()
                builder.line(
                    12f + 7.5f * rayCos, 12f + 7.5f * raySin,
                    12f + 10.5f * rayCos, 12f + 10.5f * raySin,
                )
            }
        }
        7 -> { // Waves
            builder.wave(yOffset = 0f)
            builder.wave(yOffset = 6f)
        }
        8 -> { // Diamond
            builder.path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                lineTo(21f, 12f)
                lineTo(12f, 21f)
                lineTo(3f, 12f)
                close()
            }
            builder.polygon(
                regularPolygon(12f, 12f, 4.95f, sides = 4, startAngleDegrees = 45.0),
                filled = false,
            )
        }
        else -> { // Dot grid
            listOf(6f, 12f, 18f).forEach { x ->
                listOf(6f, 12f, 18f).forEach { y ->
                    builder.filledCircle(x, y, 1.6f)
                }
            }
        }
    }
    return builder.build()
}

private fun ImageVector.Builder.filledCircle(cx: Float, cy: Float, radius: Float) {
    path(fill = SolidColor(Color.Black)) {
        moveTo(cx + radius, cy)
        arcTo(radius, radius, 0f, true, true, cx - radius, cy)
        arcTo(radius, radius, 0f, true, true, cx + radius, cy)
    }
}

private fun ImageVector.Builder.strokedCircle(cx: Float, cy: Float, radius: Float) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = GeometryStrokeWidth,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(cx + radius, cy)
        arcTo(radius, radius, 0f, true, true, cx - radius, cy)
        arcTo(radius, radius, 0f, true, true, cx + radius, cy)
    }
}

private fun ImageVector.Builder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = GeometryStrokeWidth,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }
}

private fun ImageVector.Builder.polygon(points: List<Pair<Float, Float>>, filled: Boolean) {
    path(
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = GeometryStrokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        close()
    }
}

private fun ImageVector.Builder.wave(yOffset: Float) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = GeometryStrokeWidth,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(3f, 9f + yOffset)
        curveTo(6f, 5f + yOffset, 9f, 13f + yOffset, 12f, 9f + yOffset)
        curveTo(15f, 5f + yOffset, 18f, 13f + yOffset, 21f, 9f + yOffset)
    }
}

private fun regularPolygon(
    centerX: Float,
    centerY: Float,
    radius: Float,
    sides: Int,
    startAngleDegrees: Double,
): List<Pair<Float, Float>> = (0 until sides).map { i ->
    val angle = Math.toRadians(startAngleDegrees + i * (360.0 / sides))
    (centerX + radius * cos(angle)).toFloat() to (centerY + radius * sin(angle)).toFloat()
}
