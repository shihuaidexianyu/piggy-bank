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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDatePickerDialogHost
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.util.TimeRangeUtils
import java.time.LocalDate
import java.time.ZoneId

private enum class HistoryFilterSheet {
    OVERVIEW,
    ACCOUNT,
    DATE,
    AMOUNT,
    DIRECTION,
}

private enum class HistoryDateField {
    START,
    END,
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onKeywordChange: (String) -> Unit,
    onExcludeKeywordChange: (String) -> Unit,
    onAccountChange: (Long?) -> Unit,
    onDateRangeChange: (Long?, Long?) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onAmountDirectionChange: (AmountDirectionFilter) -> Unit,
    onLoadMore: () -> Unit,
    onRecordClick: (HistoryRecordUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    var dateField by remember { mutableStateOf<HistoryDateField?>(null) }

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
            HistoryDateField.END -> state.dateEndAt ?: state.dateStartAt
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
                            selected?.let { DateTimeTextFormatter.endOfDayMillis(it) },
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
                HistoryFilterSheet.DATE -> "日期"
                HistoryFilterSheet.AMOUNT -> "金额"
                HistoryFilterSheet.DIRECTION -> "方向"
                else -> ""
            },
            onDismiss = { sheet = null },
        ) {
            when (current) {
                HistoryFilterSheet.OVERVIEW -> {
                    MoneyCard(contentPadding = PaddingValues(0.dp)) {
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
                    if (hasActiveFilters(state)) {
                        Text(
                            text = "已启用 ${activeFilterCount(state)} 项筛选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                                val todayEnd = DateTimeTextFormatter.endOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(todayStart, todayEnd)
                            },
                        )
                        QuickDateChip(
                            label = "最近 7 天",
                            onClick = {
                                val today = LocalDate.now()
                                val start = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val end = DateTimeTextFormatter.endOfDayMillis(System.currentTimeMillis())
                                onDateRangeChange(start, end)
                            },
                        )
                        QuickDateChip(
                            label = "本月",
                            onClick = {
                                val range = TimeRangeUtils.currentMonthRange()
                                onDateRangeChange(range.startAtMillis, range.endAtMillis)
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
                        value = state.dateEndAt?.let(DateTimeTextFormatter::formatDateOnly) ?: "不限",
                        modifier = Modifier.clickable { dateField = HistoryDateField.END },
                    )
                }
                HistoryFilterSheet.AMOUNT -> {
                    MoneySingleLineField(
                        value = state.minAmountText,
                        onValueChange = onMinAmountChange,
                        label = "最小金额",
                    )
                    MoneySingleLineField(
                        value = state.maxAmountText,
                        onValueChange = onMaxAmountChange,
                        label = "最大金额",
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
        trailing = {
            Text(
                text = historyCountText(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MoneyCard {
                SearchField(
                    value = state.keyword,
                    onValueChange = onKeywordChange,
                    placeholder = "包含关键词",
                )
                SearchField(
                    value = state.excludeKeyword,
                    onValueChange = onExcludeKeywordChange,
                    placeholder = "排除关键词",
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
                            modifier = Modifier.clickable {
                                onAccountChange(null)
                                onKeywordChange("")
                                onExcludeKeywordChange("")
                                onDateRangeChange(null, null)
                                onMinAmountChange("")
                                onMaxAmountChange("")
                                onAmountDirectionChange(AmountDirectionFilter.ALL)
                            },
                        )
                    }
                }
                activeFilterSummary(state)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.records.isEmpty() && state.isLoading) {
            item {
                MoneyEmptyStateCard(
                    title = "正在加载",
                    subtitle = "历史记录加载中。",
                )
            }
        } else if (state.records.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = "还没有记录",
                    subtitle = "记下第一笔入账、出账或转账后，这里会按时间线展示。",
                )
            }
        } else {
            itemsIndexed(state.records, key = { _, record -> record.id }) { index, record ->
                HistoryRow(
                    record = record,
                    settings = state.settings,
                    onClick = { onRecordClick(record) },
                )
            }
            if (state.hasMoreRecords || state.isLoadingMore) {
                item {
                    if (state.isLoadingMore) {
                        Text(
                            text = "正在加载更多...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("加载更多")
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
    settings: com.shihuaidexianyu.money.domain.model.AppSettings,
    onClick: () -> Unit,
) {
    val accent = when (record.kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (record.amount > 0) LocalMoneyColors.current.income else LocalMoneyColors.current.expense
        HistoryRecordKind.TRANSFER -> LocalMoneyColors.current.transfer
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> LocalMoneyColors.current.current
    }
    MoneyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = AmountFormatter.format(record.amount, settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = when {
                        record.kind == HistoryRecordKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                        record.amount > 0 -> LocalMoneyColors.current.income
                        record.amount < 0 -> LocalMoneyColors.current.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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

private fun dateSheetSummary(state: HistoryUiState): String {
    val start = state.dateStartAt
    val end = state.dateEndAt
    return if (start == null && end == null) {
        "不限"
    } else if (start != null && end != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 至 ${DateTimeTextFormatter.formatDateOnly(end)}"
    } else if (start != null) {
        "${DateTimeTextFormatter.formatDateOnly(start)} 起"
    } else {
        "截至 ${DateTimeTextFormatter.formatDateOnly(requireNotNull(end))}"
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
        state.selectedAccountId != null,
        state.dateStartAt != null || state.dateEndAt != null,
        state.minAmountText.isNotBlank() || state.maxAmountText.isNotBlank(),
        state.amountDirectionFilter != AmountDirectionFilter.ALL,
    ).count { it }
}

private fun activeFilterSummary(state: HistoryUiState): String? {
    val count = activeFilterCount(state)
    return if (count == 0) {
        null
    } else {
        "当前已启用$count 个筛选条件"
    }
}

private fun historyCountText(state: HistoryUiState): String {
    return when {
        state.isLoading && state.records.isEmpty() -> "加载中"
        state.hasMoreRecords || state.isLoadingMore -> "已加载 ${state.records.size}/${state.totalRecordCount} 条"
        else -> "${state.totalRecordCount} 条"
    }
}

private fun historyKindLabel(record: HistoryRecordUiModel): String {
    return when (record.kind) {
        HistoryRecordKind.CASH_FLOW -> if (record.amount > 0) "入账" else "出账"
        HistoryRecordKind.TRANSFER -> "转账"
        HistoryRecordKind.BALANCE_UPDATE -> if (record.amount == 0L) "余额核对" else "对账调整"
        HistoryRecordKind.BALANCE_ADJUSTMENT -> "手动调整"
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
