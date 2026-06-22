package com.shihuaidexianyu.money.domain.model

const val MAX_CURRENCY_SYMBOL_LENGTH = 4

fun normalizeCurrencySymbol(value: String): String {
    return value.trim()
        .take(MAX_CURRENCY_SYMBOL_LENGTH)
        .ifEmpty { "¥" }
}

data class AppSettings(
    val homePeriod: HomePeriod = HomePeriod.WEEK,
    val currencySymbol: String = "¥",
    val showStaleMark: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    val lastHistoryKeyword: String = "",
    val lastHistoryExcludeKeyword: String = "",
    val lastHistoryAccountId: Long = -1L,
    val lastHistoryDateStartAt: Long = -1L,
    val lastHistoryDateEndAt: Long = -1L,
    val lastHistoryMinAmountText: String = "",
    val lastHistoryMaxAmountText: String = "",
    val lastHistoryAmountDirection: String = "",
    val biometricLock: Boolean = false,
    val dynamicColor: Boolean = true,
)
