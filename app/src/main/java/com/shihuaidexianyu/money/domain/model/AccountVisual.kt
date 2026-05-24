package com.shihuaidexianyu.money.domain.model

const val DEFAULT_ACCOUNT_COLOR_NAME = "blue"

val ACCOUNT_COLOR_NAMES = listOf(
    "blue",
    "green",
    "orange",
    "purple",
    "red",
    "teal",
    "gray",
)

fun normalizeAccountColorName(value: String?): String {
    return value?.takeIf { it in ACCOUNT_COLOR_NAMES } ?: DEFAULT_ACCOUNT_COLOR_NAME
}
