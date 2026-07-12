package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.theme.LIGHT_EXPENSE_ARGB
import com.shihuaidexianyu.money.ui.theme.LIGHT_INCOME_ARGB
import com.shihuaidexianyu.money.ui.theme.DARK_EXPENSE_ARGB
import com.shihuaidexianyu.money.ui.theme.DARK_INCOME_ARGB
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertTrue
import org.junit.Test

class SemanticColorContrastTest {
    @Test
    fun `light semantic amount colors meet normal text contrast on pale surfaces`() {
        val lightSurfaces = listOf(0xFFFFFFFF, 0xFFF7F8FA, 0xFFF2F4F7)

        listOf(LIGHT_INCOME_ARGB, LIGHT_EXPENSE_ARGB).forEach { foreground ->
            lightSurfaces.forEach { background ->
                assertTrue(
                    contrastRatio(foreground, background) >= 4.5,
                    "contrast ${contrastRatio(foreground, background)} for ${foreground.toString(16)}",
                )
            }
        }
    }

    @Test
    fun `dark semantic amount colors meet normal text contrast on dark surfaces`() {
        val darkSurfaces = listOf(0xFF141210, 0xFF201C18, 0xFF2B2621)

        listOf(DARK_INCOME_ARGB, DARK_EXPENSE_ARGB).forEach { foreground ->
            darkSurfaces.forEach { background ->
                assertTrue(contrastRatio(foreground, background) >= 4.5)
            }
        }
    }
}

private fun contrastRatio(first: Long, second: Long): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    return (max(firstLuminance, secondLuminance) + 0.05) /
        (min(firstLuminance, secondLuminance) + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val encoded = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (encoded <= 0.04045) encoded / 12.92 else ((encoded + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
