package com.shihuaidexianyu.money.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.SwipeRevealAction
import com.shihuaidexianyu.money.ui.common.SwipeRevealActionsBox
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.RecordKindDot
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.domain.model.HistoryFilterSummary
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.LocalDate
import java.time.ZoneId

private enum class HistoryFilterSheet {
    OVERVIEW,
    TYPE,
    ACCOUNT,
    DATE,
    AMOUNT,
    DIRECTION,
}

private enum class HistoryDateField {
    START,
    END,
}

private const val HISTORY_PREFETCH_ITEM_DISTANCE = 8

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onKeywordChange: (String) -> Unit,
    onExcludeKeywordChange: (String) -> Unit,
    onRecordTypesChange: (Set<HistoryRecordType>) -> Unit,
    onAccountChange: (Long?) -> Unit,
    onDateRangeChange: (Long?, Long?) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onAmountDirectionChange: (AmountDirectionFilter) -> Unit,
    onClearAllFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onRecordClick: (HistoryRecordUiModel) -> Unit,
    historySwipeDeleteEnabled: Boolean = true,
    historySwipeEditEnabled: Boolean = true,
    onDeleteRecord: (HistoryRecordUiModel) -> Unit = {},
    onEditRecord: (HistoryRecordUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    onRetryLoadMore: () -> Unit = onLoadMore,
    onRetry: () -> Unit = {},
) {
    var sheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    var dateField by remember { mutableStateOf<HistoryDateField?>(null) }
    val listState = rememberLazyListState()
    val canPrefetch = state.hasMoreRecords &&
        !state.isLoading &&
        !state.isLoadingMore &&
        state.loadMoreErrorMessageRes == null
    val historyLoadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
    val shouldPrefetch by remember(listState, canPrefetch, state.records.size) {
        derivedStateOf {
            if (!canPrefetch) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val totalItemsCount = layoutInfo.totalItemsCount
                totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - HISTORY_PREFETCH_ITEM_DISTANCE
            }
        }
    }

    LaunchedEffect(shouldPrefetch, state.records.size) {
        if (shouldPrefetch) {
            onLoadMore()
        }
    }

    if (sheet == HistoryFilterSheet.ACCOUNT) {
        AccountPickerDialog(
            title = stringResource(R.string.history_filter_account),
            accounts = state.accountOptions,
            selectedAccountId = state.selectedAccountId,
            noSelectionLabel = stringResource(R.string.history_all_accounts),
            onDismiss = { sheet = null },
            onPick = { accountId ->
                onAccountChange(accountId)
                sheet = null
            },
            onClearSelection = {
                onAccountChange(null)
                sheet = null
            },
        )
    }

    dateField?.let { currentField ->
        val initialSelection = when (currentField) {
            HistoryDateField.START -> state.dateStartAt ?: state.dateEndAt
            HistoryDateField.END -> state.dateEndAt
                ?.let(DateTimeTextFormatter::startOfDisplayedEndDateMillis)
                ?: state.dateStartAt
        }
        MoneyDatePickerDialogHost(
            initialSelectedDateMillis = initialSelection,
            onDismiss = { dateField = null },
            onConfirm = { selected ->
                when (currentField) {
                    HistoryDateField.START -> {
                        onDateRangeChange(
                            selected?.let { DateTimeTextFormatter.startOfDayMillis(it) },
                            state.dateEndAt,
                        )
                    }
                    HistoryDateField.END -> {
                        onDateRangeChange(
                            state.dateStartAt,
                            selected?.let { DateTimeTextFormatter.endExclusiveOfDayMillis(it) },
                        )
                    }
                }
                dateField = null
            },
        )
    }

    sheet?.takeIf { it != HistoryFilterSheet.ACCOUNT }?.let { current ->
        HistoryFilterSheetContent(
            title = when (current) {
                HistoryFilterSheet.OVERVIEW -> stringResource(R.string.history_filter)
                HistoryFilterSheet.TYPE -> stringResource(R.string.history_type)
                HistoryFilterSheet.DATE -> stringResource(R.string.field_date)
                HistoryFilterSheet.AMOUNT -> stringResource(R.string.field_amount)
                HistoryFilterSheet.DIRECTION -> stringResource(R.string.history_direction)
                else -> ""
            },
            onDismiss = { sheet = null },
        ) {
            when (current) {
                HistoryFilterSheet.OVERVIEW -> {
                    MoneySingleLineField(
                        value = state.excludeKeyword,
                        onValueChange = onExcludeKeywordChange,
                        label = stringResource(R.string.history_exclude_keyword),
                    )
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        MoneyListRow(
                            title = stringResource(R.string.history_type),
                            trailing = typeSheetSummary(state),
                            onClick = { sheet = HistoryFilterSheet.TYPE },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.accounts_title),
                            trailing = accountSheetSummary(state),
                            onClick = { sheet = HistoryFilterSheet.ACCOUNT },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.field_date),
                            trailing = dateSheetSummary(state),
                            onClick = { sheet = HistoryFilterSheet.DATE },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.field_amount),
                            trailing = amountChipLabel(state),
                            onClick = { sheet = HistoryFilterSheet.AMOUNT },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = stringResource(R.string.history_direction),
                            trailing = directionChipLabel(state),
                            onClick = { sheet = HistoryFilterSheet.DIRECTION },
                        )
                    }
                }
                HistoryFilterSheet.TYPE -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HistoryRecordType.entries.forEach { option ->
                            FilterChip(
                                selected = option in state.selectedRecordTypes,
                                onClick = {
                                    onRecordTypesChange(
                                        if (option in state.selectedRecordTypes) {
                                            state.selectedRecordTypes - option
                                        } else {
                                            state.selectedRecordTypes + option
                                        },
                                    )
                                },
                                label = { Text(historyTypeLabel(option)) },
                            )
                        }
                    }
                    if (state.selectedRecordTypes.isNotEmpty()) {
                        MoneyTonalButton(onClick = { onRecordTypesChange(emptySet()) }) {
                            Text(stringResource(R.string.history_show_all_types))
                        }
                    }
                }
                HistoryFilterSheet.DATE -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickDateChip(
                            label = stringResource(R.string.history_today),
                            onClick = {
                                val todayStart = DateTimeTextFormatter.startOfDayMillis(System.currentTimeMillis())
                                val todayEnd = DateTimeTextFormatter.endExclusiveOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(todayStart, todayEnd)
                            },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.history_last_seven_days),
                            onClick = {
                                val today = LocalDate.now()
                                val start = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val end = DateTimeTextFormatter.endExclusiveOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(start, end)
                            },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.history_this_month),
                            onClick = {
                                val range = TimeRangeCalculator.currentMonthRange(
                                    zoneId = ZoneId.systemDefault(),
                                    nowMillis = System.currentTimeMillis(),
                                )
                                onDateRangeChange(range.startInclusive, range.endExclusive)
                            },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.action_clear),
                            onClick = { onDateRangeChange(null, null) },
                        )
                    }
                    MoneySelectionField(
                        label = stringResource(R.string.history_start_date),
                        value = state.dateStartAt?.let(DateTimeTextFormatter::formatDateOnly)
                            ?: stringResource(R.string.history_unlimited),
                        onClick = { dateField = HistoryDateField.START },
                    )
                    MoneySelectionField(
                        label = stringResource(R.string.history_end_date),
                        value = historyEndDateFieldText(
                            state.dateEndAt,
                            unlimitedLabel = stringResource(R.string.history_unlimited),
                        ),
                        onClick = { dateField = HistoryDateField.END },
                    )
                }
                HistoryFilterSheet.AMOUNT -> {
                    MoneySingleLineField(
                        value = state.minAmountText,
                        onValueChange = onMinAmountChange,
                        label = stringResource(R.string.history_min_amount),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.minAmountErrorRes != null,
                        supportingText = state.minAmountErrorRes?.let { stringResource(it) },
                    )
                    MoneySingleLineField(
                        value = state.maxAmountText,
                        onValueChange = onMaxAmountChange,
                        label = stringResource(R.string.history_max_amount),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.maxAmountErrorRes != null,
                        supportingText = state.maxAmountErrorRes?.let { stringResource(it) },
                    )
                }
                HistoryFilterSheet.DIRECTION -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AmountDirectionFilter.entries.forEach { option ->
                            FilterChip(
                                selected = state.amountDirectionFilter == option,
                                onClick = { onAmountDirectionChange(option) },
                                label = { Text(stringResource(option.labelRes)) },
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    val recordGroups = state.records.groupBy { DateTimeTextFormatter.formatDateOnly(it.occurredAt) }

    MoneyFormPage(
        title = stringResource(R.string.history_title),
        modifier = modifier,
        listState = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = MoneyDimens.bottomNavContentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchField(
                    value = state.keyword,
                    onValueChange = onKeywordChange,
                    placeholder = stringResource(R.string.history_include_keyword),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = hasActiveFilters(state),
                        onClick = { sheet = HistoryFilterSheet.OVERVIEW },
                        label = { Text(filterChipLabel(state)) },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.hasCommittedContent) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.history_loaded_count,
                                    state.records.size,
                                    state.records.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hasActiveFilters(state)) {
                            TextButton(onClick = onClearAllFilters) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                }
                if (hasActiveFilters(state)) {
                    ActiveFilterChips(
                        state = state,
                        onOpenSheet = { sheet = it },
                    )
                }
                state.filterSummary?.let { summary ->
                    HistoryFilterSummaryRow(summary = summary, settings = state.settings)
                }
            }
        }
        when (val content = state.toAsyncContent(historyLoadErrorMessage)) {
            AsyncContent.Loading,
            is AsyncContent.Error,
            -> item {
                AsyncContentRenderer(
                    content = content,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            is AsyncContent.Empty -> item {
                MoneyEmptyStateCard(
                    title = when (content.kind) {
                        EmptyKind.COMPLETELY_EMPTY -> stringResource(R.string.history_empty)
                        EmptyKind.FILTERED_EMPTY -> stringResource(R.string.history_filtered_empty)
                    },
                    subtitle = when (content.kind) {
                        EmptyKind.COMPLETELY_EMPTY -> stringResource(R.string.history_empty_description)
                        EmptyKind.FILTERED_EMPTY -> stringResource(R.string.history_filtered_empty_description)
                    },
                    icon = Icons.Rounded.Search,
                )
            }
            is AsyncContent.Data,
            is AsyncContent.Refreshing,
            -> {
                val nowMillis = System.currentTimeMillis()
                recordGroups.forEach { (dateLabel, records) ->
                    stickyHeader(key = "history_date_$dateLabel") {
                        HistoryDateHeader(
                            dateLabel = historyDayLabelText(
                                HistoryDayLabel.classify(
                                    occurredAt = records.first().occurredAt,
                                    nowMillis = nowMillis,
                                ),
                            ),
                            cashIncomeTotal = records
                                .filter { it.kind == HistoryRecordKind.CASH_FLOW && it.amount > 0L }
                                .map { it.amount }
                                .ledgerSumExact(),
                            cashExpenseTotal = records
                                .filter { it.kind == HistoryRecordKind.CASH_FLOW && it.amount < 0L }
                                .map { it.amount }
                                .ledgerSumExact(),
                            settings = state.settings,
                        )
                    }
                    item(key = "history_day_card_$dateLabel") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column {
                                records.forEachIndexed { index, record ->
                                    HistoryRow(
                                        record = record,
                                        settings = state.settings,
                                        onClick = { onRecordClick(record) },
                                        deleteEnabled = historySwipeDeleteEnabled && record.canMutate,
                                        editEnabled = historySwipeEditEnabled &&
                                            record.canMutate &&
                                            (record.kind == HistoryRecordKind.CASH_FLOW ||
                                                record.kind == HistoryRecordKind.TRANSFER),
                                        onDelete = { onDeleteRecord(record) },
                                        onEdit = { onEditRecord(record) },
                                    )
                                    if (index != records.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                state.loadMoreErrorMessageRes?.let { messageRes ->
                item {
                    MoneyEmptyStateCard(
                        title = stringResource(messageRes),
                        subtitle = stringResource(R.string.history_loaded_records_retained),
                    ) {
                        MoneyTonalButton(onClick = onRetryLoadMore) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Whole-filtered-set totals shown while any filter is active: cash in, cash out, and the real
 * net change those records caused (cash plus reconciliation/adjustment deltas; transfers count
 * only when the filter scopes to a single account). Values come from an aggregate query over
 * the FULL match set, so they stay correct however many pages are loaded.
 */
@Composable
private fun HistoryFilterSummaryRow(
    summary: HistoryFilterSummary,
    settings: PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HistorySummaryCell(
                label = stringResource(R.string.history_summary_inflow),
                value = formatInAppAmount(summary.cashInflow, settings),
                color = moneyColors.income,
                modifier = Modifier.weight(1f),
            )
            HistorySummaryCell(
                label = stringResource(R.string.history_summary_outflow),
                value = formatInAppAmount(summary.cashOutflow, settings),
                color = moneyColors.expense,
                modifier = Modifier.weight(1f),
            )
            HistorySummaryCell(
                label = stringResource(R.string.history_summary_net_change),
                value = signedFormatInAppAmount(summary.netChange, settings),
                color = when {
                    summary.netChange > 0L -> moneyColors.income
                    summary.netChange < 0L -> moneyColors.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistorySummaryCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun historyDayLabelText(dayLabel: HistoryDayLabel): String = when (dayLabel) {
    HistoryDayLabel.Today -> stringResource(R.string.history_today)
    HistoryDayLabel.Yesterday -> stringResource(R.string.history_yesterday)
    is HistoryDayLabel.SameYear -> stringResource(
        R.string.history_day_label_same_year,
        dayLabel.month,
        dayLabel.dayOfMonth,
    )
    is HistoryDayLabel.OtherYear -> stringResource(
        R.string.history_day_label_other_year,
        dayLabel.year,
        dayLabel.month,
        dayLabel.dayOfMonth,
    )
}

@Composable
private fun HistoryDateHeader(
    dateLabel: String,
    cashIncomeTotal: Long,
    cashExpenseTotal: Long,
    settings: PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (cashIncomeTotal > 0L) {
                    Text(
                        text = "+${formatInAppAmount(cashIncomeTotal, settings)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = moneyColors.income,
                        maxLines = 1,
                    )
                }
                if (cashExpenseTotal < 0L) {
                    Text(
                        text = formatInAppAmount(cashExpenseTotal, settings),
                        style = MaterialTheme.typography.labelMedium,
                        color = moneyColors.expense,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChips(
    state: HistoryUiState,
    onOpenSheet: (HistoryFilterSheet) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.excludeKeyword.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.OVERVIEW) },
                label = { Text(stringResource(R.string.history_excluding_keyword, state.excludeKeyword)) },
            )
        }
        if (state.selectedRecordTypes.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.TYPE) },
                label = { Text(typeSheetSummary(state)) },
            )
        }
        if (state.selectedAccountId != null) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.ACCOUNT) },
                label = { Text(accountSheetSummary(state)) },
            )
        }
        if (state.dateStartAt != null || state.dateEndAt != null) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.DATE) },
                label = { Text(dateSheetSummary(state)) },
            )
        }
        if (state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.AMOUNT) },
                label = { Text(amountChipLabel(state)) },
            )
        }
        if (state.amountDirectionFilter != AmountDirectionFilter.ALL) {
            FilterChip(
                selected = true,
                onClick = { onOpenSheet(HistoryFilterSheet.DIRECTION) },
                label = { Text(directionChipLabel(state)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterSheetContent(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneySectionHeader(title = title)
            content()
        }
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecordUiModel,
    settings: PortableSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deleteEnabled: Boolean = true,
    editEnabled: Boolean = false,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val moneyColors = LocalMoneyColors.current
    val amountText = formatInAppAmount(record.amount, settings)
    val amountStyle = when {
        amountText.length > 16 -> MaterialTheme.typography.labelMedium
        amountText.length > 12 -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.titleMedium
    }
    val kindLabel = historyKindLabel(record)
    val amountColor = when (record.kind) {
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        else -> when {
            record.amount > 0 -> moneyColors.income
            record.amount < 0 -> moneyColors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val deleteAction = if (deleteEnabled) {
        SwipeRevealAction(
            label = stringResource(R.string.action_delete),
            icon = Icons.Rounded.Delete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = MaterialTheme.colorScheme.error,
        )
    } else {
        null
    }
    val editAction = if (editEnabled) {
        SwipeRevealAction(
            label = stringResource(R.string.action_edit),
            icon = Icons.Rounded.Edit,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = MaterialTheme.colorScheme.primary,
        )
    } else {
        null
    }
    SwipeRevealActionsBox(
        endAction = deleteAction,
        startAction = editAction,
        onEndAction = onDelete,
        onStartAction = onEdit,
        contentClick = onClick,
        modifier = modifier,
    ) { contentClick ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = contentClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(record.title)
                        append("，$kindLabel")
                        append("，$amountText")
                        append("，${DateTimeTextFormatter.format(record.occurredAt)}")
                    }
                    role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecordKindDot(kind = record.kind, amount = record.amount)
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "$kindLabel · ${record.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.weight(0.42f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = amountText,
                    style = amountStyle,
                    color = amountColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = DateTimeTextFormatter.formatTimeOnly(record.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun accountSheetSummary(state: HistoryUiState): String {
    val account = state.accountOptions.firstOrNull { it.id == state.selectedAccountId }
    return account?.name ?: stringResource(R.string.history_all_accounts)
}

internal fun historyEndDateFieldText(
    endExclusive: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    unlimitedLabel: String,
): String = endExclusive
    ?.let { DateTimeTextFormatter.formatDisplayedEndDate(it, zoneId) }
    ?: unlimitedLabel

@Composable
private fun dateSheetSummary(state: HistoryUiState): String {
    val start = state.dateStartAt
    val end = state.dateEndAt
    return if (start == null && end == null) {
        stringResource(R.string.history_unlimited)
    } else if (start != null && end != null) {
        stringResource(
            R.string.history_date_range_format,
            DateTimeTextFormatter.formatDateOnly(start),
            DateTimeTextFormatter.formatDisplayedEndDate(end),
        )
    } else if (start != null) {
        stringResource(R.string.history_date_from_format, DateTimeTextFormatter.formatDateOnly(start))
    } else {
        stringResource(
            R.string.history_date_until_format,
            DateTimeTextFormatter.formatDisplayedEndDate(requireNotNull(end)),
        )
    }
}

@Composable
private fun amountChipLabel(state: HistoryUiState): String {
    return if (state.minAmountText.isBlank() && state.maxAmountText.isBlank()) {
        stringResource(R.string.field_amount)
    } else {
        stringResource(R.string.history_amount_filtered)
    }
}

@Composable
private fun directionChipLabel(state: HistoryUiState): String {
    return if (state.amountDirectionFilter == AmountDirectionFilter.ALL) {
        stringResource(R.string.history_direction)
    } else {
        stringResource(state.amountDirectionFilter.labelRes)
    }
}

@Composable
private fun typeSheetSummary(state: HistoryUiState): String = when (state.selectedRecordTypes.size) {
    0 -> stringResource(R.string.history_all_types)
    1 -> historyTypeLabel(state.selectedRecordTypes.single())
    else -> pluralStringResource(
        R.plurals.history_selected_type_count,
        state.selectedRecordTypes.size,
        state.selectedRecordTypes.size,
    )
}

@Composable
private fun historyTypeLabel(type: HistoryRecordType): String = when (type) {
    HistoryRecordType.CASH_FLOW -> stringResource(R.string.history_cash_flow)
    HistoryRecordType.TRANSFER -> stringResource(R.string.history_transfer)
    HistoryRecordType.BALANCE_UPDATE -> stringResource(R.string.history_balance_update)
    HistoryRecordType.BALANCE_ADJUSTMENT -> stringResource(R.string.history_balance_adjustment)
}

@Composable
private fun filterChipLabel(state: HistoryUiState): String {
    val count = activeFilterCount(state)
    return if (count == 0) {
        stringResource(R.string.history_filter)
    } else {
        stringResource(R.string.history_filter_count_format, count)
    }
}

private fun hasActiveFilters(state: HistoryUiState): Boolean {
    return activeFilterCount(state) > 0
}

private fun activeFilterCount(state: HistoryUiState): Int {
    return listOf(
        state.keyword.isNotBlank(),
        state.excludeKeyword.isNotBlank(),
        state.selectedRecordTypes.isNotEmpty(),
        state.selectedAccountId != null,
        state.dateStartAt != null || state.dateEndAt != null,
        state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank(),
        state.amountDirectionFilter != AmountDirectionFilter.ALL,
    ).count { it }
}

@Composable
private fun historyKindLabel(record: HistoryRecordUiModel): String {
    return when (record.kind) {
        HistoryRecordKind.CASH_FLOW -> stringResource(
            if (record.amount > 0) R.string.history_inflow else R.string.history_outflow,
        )
        HistoryRecordKind.TRANSFER -> stringResource(R.string.history_transfer)
        HistoryRecordKind.BALANCE_UPDATE -> stringResource(
            when {
                record.amount == 0L -> R.string.history_balance_update
                // On an investment account the reconciliation delta IS the investment result.
                record.isInvestmentAccount && record.amount > 0L -> R.string.history_investment_gain
                record.isInvestmentAccount -> R.string.history_investment_loss
                else -> R.string.history_reconciliation_adjustment
            },
        )
        HistoryRecordKind.BALANCE_ADJUSTMENT -> stringResource(R.string.history_balance_adjustment)
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
    )
}
