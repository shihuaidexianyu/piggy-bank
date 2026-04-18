package com.shihuaidexianyu.money.domain.model

data class AppSettings(
    val homePeriod: HomePeriod = HomePeriod.WEEK,
    val currencySymbol: String = "¥",
    val showStaleMark: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accountGroupOrder: List<AccountGroupType> = AccountGroupType.entries,
    val lastHistoryKeyword: String = "",
    val lastHistoryAccountId: Long = -1L,
    val lastHistoryDateStartAt: Long = -1L,
    val lastHistoryDateEndAt: Long = -1L,
    val lastHistoryMinAmountText: String = "",
    val lastHistoryMaxAmountText: String = "",
    val lastHistoryAmountDirection: String = "",
)

