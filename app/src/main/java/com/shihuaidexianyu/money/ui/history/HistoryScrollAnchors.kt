package com.shihuaidexianyu.money.ui.history

/**
 * Scroll-anchor index math for the history LazyColumn.
 *
 * Item layout in `HistoryScreen`: one leading header item (search field, filter chips and the
 * filter summary row all live INSIDE it), then per date group a sticky date header followed by a
 * sequence of individually keyed record rows.
 */
internal const val HISTORY_HEADER_ITEM_COUNT = 1

/**
 * Index of the sticky date-header item for [anchorDateLabel], or null when the anchor is absent
 * from the loaded page (the mutation removed that day, or the first page does not reach it).
 * [recordDateLabels] carries one formatted date label per loaded record, in list order, so the
 * the offset includes both preceding date headers and preceding record rows.
 */
internal fun historyAnchorScrollIndex(
    anchorDateLabel: String?,
    recordDateLabels: List<String>,
): Int? {
    if (anchorDateLabel == null) return null
    val groupIndex = recordDateLabels.distinct().indexOf(anchorDateLabel)
    if (groupIndex < 0) return null
    return HISTORY_HEADER_ITEM_COUNT + groupIndex + recordDateLabels.indexOf(anchorDateLabel)
}

/** Select the balance belonging to the scoped account, including receiving-side transfers. */
internal fun historyAccountBalanceAfter(record: HistoryRecordUiModel, accountId: Long): Long? = when {
    accountId !in record.accountIds -> null
    record.primaryAccountId == accountId -> record.balanceAfter
    record.kind != HistoryRecordKind.TRANSFER -> record.balanceAfter
    record.primaryAccountId != null -> record.relatedBalanceAfter
    else -> null
}
