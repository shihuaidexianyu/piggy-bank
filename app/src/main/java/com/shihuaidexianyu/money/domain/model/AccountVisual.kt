package com.shihuaidexianyu.money.domain.model

const val DEFAULT_ACCOUNT_COLOR_NAME = "blue"
const val DEFAULT_ACCOUNT_ICON_NAME = "wallet"

val ACCOUNT_ICON_NAMES = listOf(
    "wallet",
    "bank",
    "cash",
    "credit_card",
    "savings",
    "investment",
    "chart",
    "currency",
    "home",
    "phone",
    "shopping",
    "restaurant",
    "car",
    "flight",
    "gift",
    "school",
    "medical",
    "pets",
    "work",
)

/**
 * Generated geometric patterns shown in the account picker. Unlike the legacy icon catalog
 * ([ACCOUNT_ICON_NAMES]), these names select a procedurally drawn motif instead of a semantic
 * icon — each account gets a distinctive abstract badge derived from its color + pattern slot.
 * Legacy names stay valid forever (they map to a fixed pattern slot) so backups never break.
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
