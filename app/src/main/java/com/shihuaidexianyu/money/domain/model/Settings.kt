package com.shihuaidexianyu.money.domain.model

const val MAX_CURRENCY_SYMBOL_LENGTH = 4
const val MAX_RECENT_ACCOUNT_IDS = 5

fun normalizeCurrencySymbol(value: String): String =
    value.trim().take(MAX_CURRENCY_SYMBOL_LENGTH).ifEmpty { "¥" }

data class PortableSettings(
    val currencySymbol: String = "¥",
    val amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    val monthlyBudgetAmount: Long? = null,
)

enum class AppRelockDelay(val value: String) {
    IMMEDIATELY("immediately"),
    THIRTY_SECONDS("30_seconds"),
    ONE_MINUTE("1_minute"),
    FIVE_MINUTES("5_minutes"),
    ;

    companion object {
        fun fromValue(value: String?): AppRelockDelay =
            entries.firstOrNull { it.value == value } ?: THIRTY_SECONDS
    }
}

data class HistoryFilters(
    val keyword: String = "",
    val excludeKeyword: String = "",
    val accountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val amountDirection: String = "",
)

data class DevicePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val biometricLock: Boolean = false,
    val relockDelay: AppRelockDelay = AppRelockDelay.THIRTY_SECONDS,
    val maskAmountsInApp: Boolean = false,
    val hideWidgetAmounts: Boolean = false,
    val hideNotificationAmounts: Boolean = false,
    val hideRecentTasks: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val historyFilters: HistoryFilters = HistoryFilters(),
    val recentAccountIds: List<Long> = emptyList(),
)

fun normalizeRecentAccountIds(accountIds: List<Long>): List<Long> =
    accountIds.asSequence()
        .filter { it > 0L }
        .distinct()
        .take(MAX_RECENT_ACCOUNT_IDS)
        .toList()

fun requireValidMonthlyBudget(amount: Long?) {
    require(amount == null || amount > 0L) { "月预算必须大于 0" }
}
