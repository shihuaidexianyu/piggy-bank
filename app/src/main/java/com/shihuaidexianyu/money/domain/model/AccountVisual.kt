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
    return value?.takeIf { it in ACCOUNT_ICON_NAMES } ?: DEFAULT_ACCOUNT_ICON_NAME
}
