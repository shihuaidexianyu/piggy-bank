package com.shihuaidexianyu.money.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.TimeRangeUtils
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    onRetryLoadMore: () -> Unit = onLoadMore,
    onRetry: () -> Unit = {},
    onRecordClick: (HistoryRecordUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    var dateField by remember { mutableStateOf<HistoryDateField?>(null) }
    val listState = rememberLazyListState()
    val canPrefetch = state.hasMoreRecords &&
        !state.isLoading &&
        !state.isLoadingMore &&
        state.loadMoreErrorMessage == null
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
            title = "筛选账户",
            accounts = state.accountOptions,
            selectedAccountId = state.selectedAccountId,
            noSelectionLabel = "全部账户",
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
                HistoryFilterSheet.OVERVIEW -> "筛选"
                HistoryFilterSheet.TYPE -> "类型"
                HistoryFilterSheet.DATE -> "日期"
                HistoryFilterSheet.AMOUNT -> "金额"
                HistoryFilterSheet.DIRECTION -> "方向"
                else -> ""
            },
            onDismiss = { sheet = null },
        ) {
            when (current) {
                HistoryFilterSheet.OVERVIEW -> {
                    MoneySingleLineField(
                        value = state.excludeKeyword,
                        onValueChange = onExcludeKeywordChange,
                        label = "排除关键词",
                    )
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
                        MoneyListRow(
                            title = "类型",
                            trailing = typeSheetSummary(state),
                            modifier = Modifier.clickable { sheet = HistoryFilterSheet.TYPE },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "账户",
                            trailing = accountSheetSummary(state),
                            modifier = Modifier.clickable { sheet = HistoryFilterSheet.ACCOUNT },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "日期",
                            trailing = dateSheetSummary(state),
                            modifier = Modifier.clickable { sheet = HistoryFilterSheet.DATE },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "金额",
                            trailing = amountChipLabel(state),
                            modifier = Modifier.clickable { sheet = HistoryFilterSheet.AMOUNT },
                        )
                        MoneySectionDivider()
                        MoneyListRow(
                            title = "方向",
                            trailing = directionChipLabel(state),
                            modifier = Modifier.clickable { sheet = HistoryFilterSheet.DIRECTION },
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
                        OutlinedButton(onClick = { onRecordTypesChange(emptySet()) }) {
                            Text("显示全部类型")
                        }
                    }
                }
                HistoryFilterSheet.DATE -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickDateChip(
                            label = "今天",
                            onClick = {
                                val todayStart = DateTimeTextFormatter.startOfDayMillis(System.currentTimeMillis())
                                val todayEnd = DateTimeTextFormatter.endExclusiveOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(todayStart, todayEnd)
                            },
                        )
                        QuickDateChip(
                            label = "最近 7 天",
                            onClick = {
                                val today = LocalDate.now()
                                val start = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val end = DateTimeTextFormatter.endExclusiveOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(start, end)
                            },
                        )
                        QuickDateChip(
                            label = "本月",
                            onClick = {
                                val range = TimeRangeUtils.currentMonthRange()
                                onDateRangeChange(range.startInclusive, range.endExclusive)
                            },
                        )
                        QuickDateChip(
                            label = "清除",
                            onClick = { onDateRangeChange(null, null) },
                        )
                    }
                    MoneySelectionField(
                        label = "开始日期",
                        value = state.dateStartAt?.let(DateTimeTextFormatter::formatDateOnly) ?: "不限",
                        modifier = Modifier.clickable { dateField = HistoryDateField.START },
                    )
                    MoneySelectionField(
                        label = "结束日期",
                        value = historyEndDateFieldText(state.dateEndAt),
                        modifier = Modifier.clickable { dateField = HistoryDateField.END },
                    )
                }
                HistoryFilterSheet.AMOUNT -> {
                    MoneySingleLineField(
                        value = state.minAmountText,
                        onValueChange = onMinAmountChange,
                        label = "最小金额",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.minAmountError != null,
                        supportingText = state.minAmountError,
                    )
                    MoneySingleLineField(
                        value = state.maxAmountText,
                        onValueChange = onMaxAmountChange,
                        label = "最大金额",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.maxAmountError != null,
                        supportingText = state.maxAmountError,
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
                                label = { Text(option.displayName) },
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    MoneyFormPage(
        title = "历史",
        modifier = modifier,
        listState = listState,
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MoneyCard {
                SearchField(
                    value = state.keyword,
                    onValueChange = onKeywordChange,
                    placeholder = "包含关键词",
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
                    if (hasActiveFilters(state)) {
                        Text(
                            text = "清除",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onClearAllFilters),
                        )
                    }
                }
            }
        }
        when (val content = state.toAsyncContent()) {
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
                        EmptyKind.COMPLETELY_EMPTY -> "还没有记录"
                        EmptyKind.FILTERED_EMPTY -> "没有符合筛选条件的记录"
                    },
                    subtitle = when (content.kind) {
                        EmptyKind.COMPLETELY_EMPTY -> "记下第一笔入账、出账或转账后，这里会按时间线展示。"
                        EmptyKind.FILTERED_EMPTY -> "筛选条件会继续保留，可以调整或清除后重试。"
                    },
                )
            }
            is AsyncContent.Data,
            is AsyncContent.Refreshing,
            -> {
                itemsIndexed(state.records, key = { _, record -> record.id }) { _, record ->
                    HistoryRow(
                        record = record,
                        settings = state.settings,
                        onClick = { onRecordClick(record) },
                    )
                }
                state.loadMoreErrorMessage?.let { message ->
                item {
                    MoneyEmptyStateCard(
                        title = message,
                        subtitle = "已加载的记录会继续保留。",
                    ) {
                        OutlinedButton(onClick = onRetryLoadMore) {
                            Text("重试")
                        }
                    }
                }
            }
            }
        }
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
        shape = RoundedCornerShape(16.dp),
    )
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
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
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
    settings: com.shihuaidexianyu.money.domain.model.PortableSettings,
    onClick: () -> Unit,
) {
    val moneyColors = LocalMoneyColors.current
    val accent = when (record.kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (record.amount > 0) moneyColors.income else moneyColors.expense
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> moneyColors.current
    }
    val amountText = formatInAppAmount(record.amount, settings)
    val amountColor = when (record.kind) {
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        else -> when {
            record.amount > 0 -> moneyColors.income
            record.amount < 0 -> moneyColors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    MoneyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(record.title)
                    append("，${historyKindLabel(record)}")
                    append("，$amountText")
                    append("，${DateTimeTextFormatter.format(record.occurredAt)}")
                }
                role = Role.Button
            },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .background(
                        color = accent,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(record.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${historyKindLabel(record)} · ${record.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleLarge,
                    color = amountColor,
                )
                Text(
                    text = DateTimeTextFormatter.format(record.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun accountChipLabel(state: HistoryUiState): String {
    val account = state.accountOptions.firstOrNull { it.id == state.selectedAccountId }
    return account?.let {
        if (it.name.length > 6) "账户已选" else it.name
    } ?: "全部账户"
}

private fun accountSheetSummary(state: HistoryUiState): String {
    val account = state.accountOptions.firstOrNull { it.id == state.selectedAccountId }
    return account?.name ?: "全部账户"
}

private fun dateChipLabel(state: HistoryUiState): String {
    val start = state.dateStartAt
    val end = state.dateEndAt
    return if (start == null && end == null) "日期" else "日期已选"
}

internal fun historyEndDateFieldText(
    endExclusive: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = endExclusive
    ?.let { DateTimeTextFormatter.formatDisplayedEndDate(it, zoneId) }
    ?: "不限"

private fun dateSheetSummary(state: HistoryUiState): String {
    val start = state.dateStartAt
    val end = state.dateEndAt
    return if (start == null && end == null) {
        "不限"
    } else if (start != null && end != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 至 ${DateTimeTextFormatter.formatDisplayedEndDate(end)}"
    } else if (start != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 起"
    } else {
        "截至 ${DateTimeTextFormatter.formatDisplayedEndDate(requireNotNull(end))}"
    }
}

private fun amountChipLabel(state: HistoryUiState): String {
    return if (state.minAmountText.isBlank() && state.maxAmountText.isBlank()) {
        "金额"
    } else {
        "金额已筛选"
    }
}

private fun directionChipLabel(state: HistoryUiState): String {
    return if (state.amountDirectionFilter == AmountDirectionFilter.ALL) "方向" else state.amountDirectionFilter.displayName
}

private fun typeSheetSummary(state: HistoryUiState): String = when (state.selectedRecordTypes.size) {
    0 -> "全部类型"
    1 -> historyTypeLabel(state.selectedRecordTypes.single())
    else -> "已选 ${state.selectedRecordTypes.size} 类"
}

private fun historyTypeLabel(type: HistoryRecordType): String = when (type) {
    HistoryRecordType.CASH_FLOW -> "收支"
    HistoryRecordType.TRANSFER -> "转账"
    HistoryRecordType.BALANCE_UPDATE -> "余额核对"
    HistoryRecordType.BALANCE_ADJUSTMENT -> "余额校正"
}

private fun filterChipLabel(state: HistoryUiState): String {
    val count = activeFilterCount(state)
    return if (count == 0) "筛选" else "筛选 $count"
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
        state.transferFromAccountId != null || state.transferToAccountId != null,
        state.dateStartAt != null || state.dateEndAt != null,
        state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank(),
        state.amountDirectionFilter != AmountDirectionFilter.ALL,
    ).count { it }
}

private fun historyKindLabel(record: HistoryRecordUiModel): String {
    return when (record.kind) {
        HistoryRecordKind.CASH_FLOW -> if (record.amount > 0) "入账" else "出账"
        HistoryRecordKind.TRANSFER -> "转账"
        HistoryRecordKind.BALANCE_UPDATE -> if (record.amount == 0L) "余额核对" else "对账调整"
        HistoryRecordKind.BALANCE_ADJUSTMENT -> "余额校正"
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
