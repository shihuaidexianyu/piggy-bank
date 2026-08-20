package com.shihuaidexianyu.money.domain.model

const val MAX_CURRENCY_SYMBOL_LENGTH = 4
const val MAX_RECENT_ACCOUNT_IDS = 5

fun normalizeCurrencySymbol(value: String): String =
    value.trim().take(MAX_CURRENCY_SYMBOL_LENGTH).ifEmpty { "¥" }

data class PortableSettings(
    val currencySymbol: String = "¥",
    val amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    val budgetAmount: Long? = null,
    val budgetPeriod: BudgetPeriod = BudgetPeriod.DEFAULT,
)

/** Which calendar window the spending budget is measured against. */
enum class BudgetPeriod(val value: String) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    ;

    companion object {
        val DEFAULT = MONTHLY

        fun fromValue(value: String?): BudgetPeriod =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** The calendar window a budget period is measured against on the dashboard. */
val BudgetPeriod.dashboardPeriod: DashboardPeriod
    get() = when (this) {
        BudgetPeriod.WEEKLY -> DashboardPeriod.WEEK
        BudgetPeriod.MONTHLY -> DashboardPeriod.MONTH
        BudgetPeriod.YEARLY -> DashboardPeriod.YEAR
    }

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
    val recordTypes: Set<String> = emptySet(),
    val accountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val amountDirection: String = "",
)

data class DevicePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Default off: the app ships a designed neutral palette; Material You wallpaper theming
    // is opt-in from settings.
    val useDynamicColor: Boolean = false,
    // Default off: record pages navigate away after a successful save. When enabled, the page
    // stays open and clears amount/note so consecutive entries can be recorded quickly.
    val continueRecording: Boolean = false,
    val biometricLock: Boolean = false,
    val relockDelay: AppRelockDelay = AppRelockDelay.THIRTY_SECONDS,
    val maskAmountsInApp: Boolean = false,
    val hideNotificationAmounts: Boolean = false,
    val hideRecentTasks: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val historyFilters: HistoryFilters = HistoryFilters(),
    val recentAccountIds: List<Long> = emptyList(),
)

fun failClosedDevicePreferences(): DevicePreferences = DevicePreferences(
    maskAmountsInApp = true,
    hideNotificationAmounts = true,
    hideRecentTasks = true,
)

fun normalizeRecentAccountIds(accountIds: List<Long>): List<Long> =
    accountIds.asSequence()
        .filter { it > 0L }
        .distinct()
        .take(MAX_RECENT_ACCOUNT_IDS)
        .toList()

fun requireValidBudgetAmount(amount: Long?) {
    require(amount == null || amount > 0L) { "预算必须大于 0" }
}
