package com.shihuaidexianyu.money.domain.model

const val DEFAULT_ACCOUNT_COLOR_NAME = "blue"
const val DEFAULT_ACCOUNT_ICON_NAME = "wallet"

/**
 * Semantic icon catalog shown in the account picker; list order is the picker's display order.
 * Names are stored on accounts and in backups, so they are stable API — never rename or remove,
 * only append.
 */
val ACCOUNT_ICON_NAMES = listOf(
    // Money containers
    "wallet",
    "cash",
    "bank",
    "credit_card",
    "savings",
    "qr_code",
    // Payment brands (glyphs in ui/common/AccountBrandIcons.kt)
    "wechat",
    "alipay",
    "receipt",
    // Investing
    "investment",
    "chart",
    "pie_chart",
    "currency",
    "currency_exchange",
    "stock",
    "insurance",
    // Life
    "home",
    "car",
    "subway",
    "flight",
    "restaurant",
    "shopping",
    "utilities",
    "entertainment",
    "medical",
    "school",
    "fitness",
    "pets",
    "child_care",
    "gift",
    "phone",
    "work",
)

/**
 * Abstract pattern names produced by a past design revision. They are no longer offered in the
 * picker, but existing accounts and old backups may still carry them, so they stay valid forever
 * and render as a fixed semantic-icon slot (see `semanticIconName` in ui/common/AccountVisuals).
 */
val ACCOUNT_GEOMETRY_NAMES = listOf(
    "geo_rings",
    "geo_pie",
    "geo_hexagon",
    "geo_arc",
    "geo_dots",
    "geo_lines",
    "geo_sun",
    "geo_wave",
    "geo_diamond",
    "geo_grid",
)

/**
 * List order is also the picker's display order, and it is deliberate: the palette was validated
 * (dataviz six-checks: lightness band, chroma floor, adjacent-pair CVD separation, normal-vision
 * floor, contrast vs surface) in exactly this adjacency, for light AND dark surfaces. Reordering
 * or adding hues requires re-running the validator, not eyeballing. `gray` is the semantic
 * neutral and sits outside the chromatic sequence.
 */
val ACCOUNT_COLOR_NAMES = listOf(
    "green",
    "purple",
    "gold",
    "blue",
    "orange",
    "teal",
    "red",
    "cyan",
    "rose",
    "indigo",
    "brown",
    "gray",
)

fun normalizeAccountColorName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_COLOR_NAMES } ?: DEFAULT_ACCOUNT_COLOR_NAME
}

fun normalizeAccountIconName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_ICON_NAMES || it in ACCOUNT_GEOMETRY_NAMES }
        ?: DEFAULT_ACCOUNT_ICON_NAME
}
