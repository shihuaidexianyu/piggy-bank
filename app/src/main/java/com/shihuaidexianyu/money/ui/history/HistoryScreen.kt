package com.shihuaidexianyu.money.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.BalanceTransitionText
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.domain.model.HistoryFilterSummary
import com.shihuaidexianyu.money.domain.model.HistoryBusinessSemantic
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private enum class HistoryFilterSheet {
    OVERVIEW,
    TYPE,
    BUSINESS,
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
    onBusinessSemanticChange: (HistoryBusinessSemantic) -> Unit = {},
    onAccountChange: (Long?) -> Unit,
    onDateRangeChange: (Long?, Long?) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onAmountDirectionChange: (AmountDirectionFilter) -> Unit,
    onClearAllFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onRecordClick: (HistoryRecordUiModel) -> Unit,
    /**
     * Account drill-down mode (`history/account/{accountId}`): the account scope is fixed by the
     * route — the title shows the account name, the account picker/chip stay hidden, and the
     * locked account is not counted as a user filter. Null on the History tab itself.
     */
    lockedAccountId: Long? = null,
    onBack: (() -> Unit)? = null,
    onRecordIncome: () -> Unit = {},
    onRecordExpense: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRetryLoadMore: () -> Unit = onLoadMore,
    onRetry: () -> Unit = {},
    onScrolledChange: (Boolean) -> Unit = {},
) {
    var sheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    var dateField by remember { mutableStateOf<HistoryDateField?>(null) }
    val listState = rememberLazyListState()
    var searchExpanded by rememberSaveable { mutableStateOf(state.keyword.isNotBlank()) }
    val searchVisibility = remember { MutableTransitionState(searchExpanded) }
    searchVisibility.targetState = searchExpanded
    var requestSearchFocus by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val closeSearch = {
        requestSearchFocus = false
        focusManager.clearFocus()
        keyboardController?.hide()
        searchExpanded = false
        onKeywordChange("")
    }
    val latestOnScrolledChange by rememberUpdatedState(onScrolledChange)
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollBackward }.collect { latestOnScrolledChange(it) }
    }
    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotBlank()) searchExpanded = true
    }
    LaunchedEffect(searchVisibility.isIdle, searchExpanded, requestSearchFocus) {
        // Focus only after the field is fully placed. Fast open/close gestures cancel this
        // request, and opening search never resets the reader's list position.
        if (searchExpanded && searchVisibility.isIdle && requestSearchFocus) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
            requestSearchFocus = false
        }
    }
    BackHandler(enabled = searchExpanded && state.keyword.isBlank()) {
        closeSearch()
    }
    state.records.firstOrNull { it.id == selectedRecordId }?.let { record ->
        HistoryRecordDetails(
            record = record,
            settings = state.settings,
            onDismiss = { selectedRecordId = null },
            onOpenRecord = {
                selectedRecordId = null
                onRecordClick(record)
            },
        )
    }
    val canPrefetch = state.hasMoreRecords &&
        !state.isLoading &&
        !state.isLoadingMore &&
        state.loadMoreErrorMessageRes == null
    // Scroll anchor: a ledger mutation reloads the first page and can strand a user who was
    // paging deep in the list. Capture the visible date while the old page is still on screen
    // (isRefreshing), then scroll back to it once the fresh page lands.
    var pendingAnchorDate by remember { mutableStateOf<String?>(null) }
    val visibleDateLabel by remember(listState) {
        derivedStateOf {
            (listState.layoutInfo.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith("history_date_") == true }?.key as? String)
                ?.takeIf { it.startsWith("history_date_") }
                ?.removePrefix("history_date_")
        }
    }
    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            pendingAnchorDate = visibleDateLabel ?: pendingAnchorDate
        } else {
            val anchor = pendingAnchorDate
            pendingAnchorDate = null
            if (anchor == null || state.records.isEmpty()) return@LaunchedEffect
            val scrollIndex = historyAnchorScrollIndex(
                anchorDateLabel = anchor,
                recordDateLabels = state.records.map { DateTimeTextFormatter.formatDateOnly(it.occurredAt) },
            ) ?: return@LaunchedEffect
            listState.scrollToItem(scrollIndex)
        }
    }
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

    if (sheet == HistoryFilterSheet.ACCOUNT && lockedAccountId == null) {
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
                HistoryFilterSheet.BUSINESS -> stringResource(R.string.history_business_semantic)
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
                            title = stringResource(R.string.history_business_semantic),
                            trailing = businessSemanticLabel(state.businessSemantic),
                            onClick = { sheet = HistoryFilterSheet.BUSINESS },
                        )
                        MoneySectionDivider()
                        if (lockedAccountId == null) {
                            MoneyListRow(
                                title = stringResource(R.string.accounts_title),
                                trailing = accountSheetSummary(state),
                                onClick = { sheet = HistoryFilterSheet.ACCOUNT },
                            )
                            MoneySectionDivider()
                        }
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
                HistoryFilterSheet.BUSINESS -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HistoryBusinessSemantic.entries.forEach { option ->
                            FilterChip(
                                selected = state.businessSemantic == option,
                                onClick = { onBusinessSemanticChange(option) },
                                label = { Text(businessSemanticLabel(option)) },
                            )
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
    val accountLocked = lockedAccountId != null
    // Locked mode: the page title IS the account name, falling back to the tab title while the
    // account list is still loading.
    val pageTitle = lockedAccountId?.let { id ->
        state.accountOptions.firstOrNull { it.id == id }?.name
    } ?: stringResource(R.string.history_title)

    MoneyFormPage(
        title = pageTitle,
        onBack = onBack,
        modifier = modifier,
        listState = listState,
        contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = MoneyDimens.bottomNavContentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        trailing = {
            IconButton(
                onClick = {
                    if (searchExpanded) {
                        closeSearch()
                    } else {
                        searchExpanded = true
                        requestSearchFocus = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = stringResource(if (searchExpanded) R.string.history_close_search else R.string.history_search),
                )
            }
            TextButton(onClick = { focusManager.clearFocus(); sheet = HistoryFilterSheet.OVERVIEW }) {
                Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(filterChipLabel(state, accountLocked), modifier = Modifier.padding(start = 4.dp))
            }
        },
        header = {
            // Search belongs to the fixed toolbar. Animate the viewport as one surface;
            // inserting it as a lazy item made sticky dates jump ahead of moving records.
            AnimatedVisibility(
                visibleState = searchVisibility,
                enter = expandVertically(tween(220), expandFrom = Alignment.Top) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
            ) {
                SearchField(
                    value = state.keyword,
                    onValueChange = onKeywordChange,
                    placeholder = stringResource(R.string.history_search),
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .focusRequester(searchFocusRequester)
                        .testTag("history_search_field"),
                )
            }
        },
    ) {
        item(key = "history_controls", contentType = "controls") {
            // Keep a measurable top anchor when search is collapsed, so an empty controls
            // item is not skipped and reported as an already-scrolled list.
            Column(modifier = Modifier.heightIn(min = 1.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasActiveFilters(state, accountLocked)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.filterMatchCount?.let { count ->
                            Text(
                                pluralStringResource(R.plurals.history_matched_count, count, count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onClearAllFilters) { Text(stringResource(R.string.action_clear)) }
                    }
                }
                if (hasActiveFilters(state, accountLocked)) {
                    ActiveFilterChips(
                        state = state,
                        accountLocked = accountLocked,
                        onRemove = { filter ->
                            when (filter) {
                                HistoryFilterSheet.OVERVIEW -> onExcludeKeywordChange("")
                                HistoryFilterSheet.TYPE -> onRecordTypesChange(emptySet())
                                HistoryFilterSheet.BUSINESS -> onBusinessSemanticChange(HistoryBusinessSemantic.ALL)
                                HistoryFilterSheet.ACCOUNT -> onAccountChange(null)
                                HistoryFilterSheet.DATE -> onDateRangeChange(null, null)
                                HistoryFilterSheet.AMOUNT -> {
                                    onMinAmountChange("")
                                    onMaxAmountChange("")
                                }
                                HistoryFilterSheet.DIRECTION -> onAmountDirectionChange(AmountDirectionFilter.ALL)
                            }
                        },
                    )
                }
                state.filterSummary?.let { summary ->
                    HistoryFilterSummaryRow(summary = summary, settings = state.settings)
                }
                if (state.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                    action = if (content.kind == EmptyKind.COMPLETELY_EMPTY) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MoneyTonalButton(
                                    onClick = onRecordIncome,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.ledger_income))
                                }
                                MoneyTonalButton(
                                    onClick = onRecordExpense,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.ledger_expense))
                                }
                            }
                        }
                    } else {
                        {
                            MoneyTonalButton(onClick = onClearAllFilters) {
                                Text(stringResource(R.string.history_clear_filters))
                            }
                        }
                    },
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
                            partialTotal = state.hasMoreRecords &&
                                state.records.lastOrNull()
                                    ?.let { DateTimeTextFormatter.formatDateOnly(it.occurredAt) } == dateLabel,
                        )
                    }
                    itemsIndexed(
                        items = records,
                        key = { _, record -> "history_record_${record.id}" },
                        contentType = { _, _ -> "record" },
                    ) { index, record ->
                        Column(modifier = Modifier.animateItem(placementSpec = null)) {
                            HistoryRow(
                                record = record,
                                settings = state.settings,
                                accountBalanceAfter = lockedAccountId?.let { historyAccountBalanceAfter(record, it) },
                                onClick = { focusManager.clearFocus(); selectedRecordId = record.id },
                            )
                            if (index != records.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
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
                if (state.isLoadingMore) {
                    item(key = "history_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
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
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
    partialTotal: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("history_date_header_$dateLabel"),
        color = MaterialTheme.colorScheme.background,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            if (cashIncomeTotal > 0L) {
                Text(
                    text = stringResource(R.string.history_day_income, formatInAppAmount(cashIncomeTotal, settings)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalMoneyColors.current.income,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (cashExpenseTotal < 0L) {
                Text(
                    text = stringResource(R.string.history_day_expense, formatInAppAmount(cashExpenseTotal, settings).removePrefix("-")),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalMoneyColors.current.expense,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (partialTotal) {
                Text(
                    text = stringResource(R.string.history_partial_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChips(
    state: HistoryUiState,
    accountLocked: Boolean,
    onRemove: (HistoryFilterSheet) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.excludeKeyword.isNotBlank()) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.OVERVIEW) },
                label = { Text(stringResource(R.string.history_excluding_keyword, state.excludeKeyword)) },
            )
        }
        if (state.selectedRecordTypes.isNotEmpty()) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.TYPE) },
                label = { Text(typeSheetSummary(state)) },
            )
        }
        if (state.businessSemantic != HistoryBusinessSemantic.ALL) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.BUSINESS) },
                label = { Text(businessSemanticLabel(state.businessSemantic)) },
            )
        }
        if (!accountLocked && state.selectedAccountId != null) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.ACCOUNT) },
                label = { Text(accountSheetSummary(state)) },
            )
        }
        if (state.dateStartAt != null || state.dateEndAt != null) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.DATE) },
                label = { Text(dateSheetSummary(state)) },
            )
        }
        if (state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank()) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.AMOUNT) },
                label = { Text(amountChipLabel(state)) },
            )
        }
        if (state.amountDirectionFilter != AmountDirectionFilter.ALL) {
            FilterChip(
                selected = true,
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_remove_filter), modifier = Modifier.size(16.dp)) },
                onClick = { onRemove(HistoryFilterSheet.DIRECTION) },
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
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneySectionHeader(title = title)
            content()
        }
    }
}

@Composable
private fun historyRecordAmount(record: HistoryRecordUiModel, settings: PortableSettings): String =
    if (record.kind == HistoryRecordKind.TRANSFER) formatInAppAmount(record.amount, settings)
    else signedFormatInAppAmount(record.amount, settings)

@Composable
private fun historyRecordColor(record: HistoryRecordUiModel): Color {
    val colors = LocalMoneyColors.current
    return when {
        record.kind == HistoryRecordKind.TRANSFER -> colors.transfer
        record.amount > 0 -> colors.income
        record.amount < 0 -> colors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecordUiModel,
    settings: PortableSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accountBalanceAfter: Long? = null,
) {
    val amountText = historyRecordAmount(record, settings)
    val amountColor = historyRecordColor(record)
    val kindLabel = historyKindLabel(record)
    val timeLabel = DateTimeTextFormatter.formatTimeOnly(record.occurredAt)
    val subtitleText = listOfNotNull(
        kindLabel.takeIf { record.kind != HistoryRecordKind.CASH_FLOW },
        record.subtitle.takeIf { it.isNotBlank() },
        timeLabel,
    ).joinToString(" · ")
    val postBalanceText = accountBalanceAfter?.let {
        stringResource(R.string.history_post_balance, formatInAppAmount(it, settings))
    }
    val stackAmount = LocalDensity.current.fontScale > 1.3f || amountText.length > 15
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_row_${record.id}")
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    record.title, kindLabel, record.subtitle.takeIf { it.isNotBlank() },
                    amountText, DateTimeTextFormatter.format(record.occurredAt), postBalanceText,
                ).joinToString("，")
                role = Role.Button
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (stackAmount) {
                Text(record.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = amountColor)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            postBalanceText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRecordDetails(
    record: HistoryRecordUiModel,
    settings: PortableSettings,
    onDismiss: () -> Unit,
    onOpenRecord: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var openingRecord by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MoneySectionHeader(title = stringResource(R.string.history_record_detail))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.title, style = MaterialTheme.typography.titleLarge)
                Text(historyRecordAmount(record, settings), style = MaterialTheme.typography.headlineLarge, color = historyRecordColor(record))
                Text(historyKindLabel(record), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MoneyInlineLabelValue(label = stringResource(R.string.account_single), value = record.subtitle)
            MoneyInlineLabelValue(label = stringResource(R.string.field_occurred_time), value = DateTimeTextFormatter.format(record.occurredAt))
            if (record.balanceBefore != null && record.balanceAfter != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (record.kind == HistoryRecordKind.TRANSFER) R.string.transfer_from_account else R.string.history_balance_evidence), style = MaterialTheme.typography.labelMedium)
                    BalanceTransitionText(before = record.balanceBefore, after = record.balanceAfter, settings = settings)
                }
            }
            if (record.kind == HistoryRecordKind.TRANSFER && record.relatedBalanceBefore != null && record.relatedBalanceAfter != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.transfer_to_account), style = MaterialTheme.typography.labelMedium)
                    BalanceTransitionText(before = record.relatedBalanceBefore, after = record.relatedBalanceAfter, settings = settings)
                }
            }
            if (record.canMutate) {
                androidx.compose.material3.Button(
                    onClick = {
                        openingRecord = true
                        scope.launch {
                            sheetState.hide()
                            onOpenRecord()
                        }
                    },
                    enabled = !openingRecord,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (record.kind == HistoryRecordKind.CASH_FLOW || record.kind == HistoryRecordKind.TRANSFER) R.string.action_edit_record else R.string.history_open_record))
                }
            } else {
                Text(stringResource(R.string.account_closed_readonly_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val nowMillis = System.currentTimeMillis()
    return if (start == null && end == null) {
        stringResource(R.string.history_unlimited)
    } else if (start != null && end != null) {
        stringResource(
            R.string.history_date_range_format,
            DateTimeTextFormatter.formatDayInYear(start, nowMillis),
            DateTimeTextFormatter.formatDayInYear(
                DateTimeTextFormatter.startOfDisplayedEndDateMillis(end),
                nowMillis,
            ),
        )
    } else if (start != null) {
        stringResource(
            R.string.history_date_from_format,
            DateTimeTextFormatter.formatDayInYear(start, nowMillis),
        )
    } else {
        stringResource(
            R.string.history_date_until_format,
            DateTimeTextFormatter.formatDayInYear(
                DateTimeTextFormatter.startOfDisplayedEndDateMillis(requireNotNull(end)),
                nowMillis,
            ),
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
private fun businessSemanticLabel(semantic: HistoryBusinessSemantic): String = stringResource(
    when (semantic) {
        HistoryBusinessSemantic.ALL -> R.string.history_business_all
        HistoryBusinessSemantic.DAILY_EXPENSE -> R.string.history_daily_expense
        HistoryBusinessSemantic.INVESTMENT_PNL -> R.string.history_investment_pnl
        HistoryBusinessSemantic.INVESTMENT_GAIN -> R.string.history_investment_gain
        HistoryBusinessSemantic.INVESTMENT_LOSS -> R.string.history_investment_loss
    },
)

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
private fun filterChipLabel(state: HistoryUiState, accountLocked: Boolean): String {
    val count = activeFilterCount(state, accountLocked)
    return if (count == 0) {
        stringResource(R.string.history_filter)
    } else {
        stringResource(R.string.history_filter_count_format, count)
    }
}

private fun hasActiveFilters(state: HistoryUiState, accountLocked: Boolean): Boolean {
    return activeFilterCount(state, accountLocked) > 0
}

// A locked account is the page's scope, not a user-chosen filter — it never counts toward
// the "筛选 · N" badge, the chip row, or the clear button.
private fun activeFilterCount(state: HistoryUiState, accountLocked: Boolean): Int {
    return listOf(
        state.keyword.isNotBlank(),
        state.excludeKeyword.isNotBlank(),
        state.selectedRecordTypes.isNotEmpty(),
        state.businessSemantic != HistoryBusinessSemantic.ALL,
        !accountLocked && state.selectedAccountId != null,
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
