package com.shihuaidexianyu.money.ui.history

/**
 * Scroll-anchor index math for the history LazyColumn.
 *
 * Item layout in `HistoryScreen`: one leading header item (search field, filter chips and the
 * filter summary row all live INSIDE it), then per date group a sticky date header followed by a
 * single day card holding that day's records — two items per date group.
 */
internal const val HISTORY_HEADER_ITEM_COUNT = 1

/**
 * Index of the sticky date-header item for [anchorDateLabel], or null when the anchor is absent
 * from the loaded page (the mutation removed that day, or the first page does not reach it).
 * [recordDateLabels] carries one formatted date label per loaded record, in list order, so the
 * group index counts DAYS — not records — which is what the two-items-per-group layout needs.
 */
internal fun historyAnchorScrollIndex(
    anchorDateLabel: String?,
    recordDateLabels: List<String>,
): Int? {
    if (anchorDateLabel == null) return null
    val groupIndex = recordDateLabels.distinct().indexOf(anchorDateLabel)
    if (groupIndex < 0) return null
    return HISTORY_HEADER_ITEM_COUNT + groupIndex * 2
}
