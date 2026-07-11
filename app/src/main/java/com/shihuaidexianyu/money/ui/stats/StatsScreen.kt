package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetRange: () -> Unit,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "分析",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MoneyDimens.bottomNavContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MonthNavigator(
                    rangeText = state.rangeText,
                    canNavigateNext = state.canNavigateNext,
                    onPrevious = onPreviousRange,
                    onNext = onNextRange,
                    onCurrent = onResetRange,
                )
            }
            when (val content = state.toAsyncContent()) {
                AsyncContent.Loading,
                is AsyncContent.Error,
                -> item {
                    AsyncContentRenderer(
                        content = content,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                        data = { _, _ -> },
                    )
                }
                is AsyncContent.Empty -> item {
                    MoneyEmptyStateCard(
                        title = "暂无可分析数据",
                        subtitle = "创建账户并记录流水后，这里会显示按自然月汇总的分析。",
                    )
                }
                is AsyncContent.Data,
                is AsyncContent.Refreshing,
                -> {
                    item { MonthlySummaryCard(state, onOpenHistory) }
                    item { AnalysisSectionTitle("每日趋势与净流") }
                    items(state.dailyPoints, key = { it.date.toEpochDay() }) { point ->
                        DailyAnalysisRow(point, onOpenHistory)
                    }
                    item { AnalysisSectionTitle("账户现金流入／流出（转账单列）") }
                    if (state.accountCashFlows.isEmpty()) item { AnalysisEmptyCard("该月没有账户现金收支") }
                    items(state.accountCashFlows, key = { it.accountId }) { flow ->
                        AccountCashFlowRow(flow, onOpenHistory)
                    }
                    item { AnalysisSectionTitle("转账路径") }
                    if (state.transferPaths.isEmpty()) item { AnalysisEmptyCard("该月没有转账") }
                    items(state.transferPaths, key = { "${it.historyFilters.transferFromAccountId}:${it.historyFilters.transferToAccountId}" }) { path ->
                        TransferPathRow(path, onOpenHistory)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    rangeText: String,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = rangeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable(onClick = onCurrent),
            )
            IconButton(onClick = onNext, enabled = canNavigateNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(state: StatsUiState, onOpenHistory: (HistoryRecordFilters) -> Unit) {
    MoneyCard(contentPadding = PaddingValues(0.dp)) {
        MoneySectionHeader(title = "月度收支")
        MoneyListRow(
            title = "收入",
            trailing = state.totalInflowText,
            modifier = Modifier.clickable { onOpenHistory(state.inflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "支出",
            trailing = state.totalOutflowText,
            modifier = Modifier.clickable { onOpenHistory(state.outflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "净现金流",
            trailing = state.netCashFlowText,
            modifier = Modifier.clickable { onOpenHistory(state.netCashFlowHistoryFilters) },
        )
    }
}

@Composable
private fun DailyAnalysisRow(point: StatsDailyUiModel, onOpenHistory: (HistoryRecordFilters) -> Unit) {
    MoneyCard(contentPadding = PaddingValues(0.dp)) {
        MoneyListRow(
            title = point.dateText,
            subtitle = "收入 ${point.inflowText} · 支出 ${point.outflowText}",
            trailing = point.netFlowText,
            modifier = Modifier.clickable { onOpenHistory(point.historyFilters) },
        )
    }
}

@Composable
private fun AccountCashFlowRow(
    flow: StatsAccountCashFlowUiModel,
    onOpenHistory: (HistoryRecordFilters) -> Unit,
) {
    MoneyCard(contentPadding = PaddingValues(0.dp)) {
        MoneyListRow(
            title = flow.name,
            subtitle = "现金流入",
            trailing = flow.inflowText,
            modifier = Modifier.clickable { onOpenHistory(flow.inflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = "${flow.name} · 现金流出",
            trailing = flow.outflowText,
            modifier = Modifier.clickable { onOpenHistory(flow.outflowHistoryFilters) },
        )
    }
}

@Composable
private fun TransferPathRow(path: StatsTransferPathUiModel, onOpenHistory: (HistoryRecordFilters) -> Unit) {
    MoneyCard(contentPadding = PaddingValues(0.dp)) {
        MoneyListRow(
            title = path.label,
            trailing = path.amountText,
            modifier = Modifier.clickable { onOpenHistory(path.historyFilters) },
        )
    }
}

@Composable
private fun AnalysisSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AnalysisEmptyCard(message: String) {
    MoneyCard { Text(message) }
}
