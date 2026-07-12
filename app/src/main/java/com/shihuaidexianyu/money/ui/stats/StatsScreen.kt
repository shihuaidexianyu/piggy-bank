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
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
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
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val loadErrorMessage = state.errorMessageRes?.let { stringResource(it) }.orEmpty()
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = stringResource(R.string.stats_title),
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
            when (val content = state.toAsyncContent(loadErrorMessage)) {
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
                        title = stringResource(R.string.stats_empty_title),
                        subtitle = stringResource(R.string.stats_empty_description),
                    )
                }
                is AsyncContent.Data,
                is AsyncContent.Refreshing,
                -> {
                    item { MonthlySummaryCard(state, onOpenHistory) }
                    item { AnalysisSectionTitle(stringResource(R.string.stats_daily_section)) }
                    items(state.dailyPoints, key = { it.date.toEpochDay() }) { point ->
                        DailyAnalysisRow(point, onOpenHistory)
                    }
                    item { AnalysisSectionTitle(stringResource(R.string.stats_account_cash_section)) }
                    if (state.accountCashFlows.isEmpty()) {
                        item { AnalysisEmptyCard(stringResource(R.string.stats_no_account_cash)) }
                    }
                    items(state.accountCashFlows, key = { it.accountId }) { flow ->
                        AccountCashFlowRow(flow, onOpenHistory)
                    }
                    item { AnalysisSectionTitle(stringResource(R.string.stats_transfer_path_section)) }
                    if (state.transferPaths.isEmpty()) {
                        item { AnalysisEmptyCard(stringResource(R.string.stats_no_transfers)) }
                    }
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
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.stats_previous_month),
                )
            }
            Text(
                text = rangeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable(onClick = onCurrent),
            )
            IconButton(onClick = onNext, enabled = canNavigateNext) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stats_next_month),
                )
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(state: StatsUiState, onOpenHistory: (HistoryRecordFilters) -> Unit) {
    MoneyCard(contentPadding = PaddingValues(0.dp)) {
        MoneySectionHeader(title = stringResource(R.string.stats_month_summary))
        MoneyListRow(
            title = stringResource(R.string.stats_income),
            trailing = state.totalInflowText,
            modifier = Modifier.clickable { onOpenHistory(state.inflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = stringResource(R.string.stats_expense),
            trailing = state.totalOutflowText,
            modifier = Modifier.clickable { onOpenHistory(state.outflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = stringResource(R.string.stats_net_cash_flow),
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
            subtitle = stringResource(R.string.stats_daily_flow_format, point.inflowText, point.outflowText),
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
            subtitle = stringResource(R.string.stats_cash_inflow),
            trailing = flow.inflowText,
            modifier = Modifier.clickable { onOpenHistory(flow.inflowHistoryFilters) },
        )
        MoneySectionDivider()
        MoneyListRow(
            title = stringResource(R.string.stats_account_outflow_format, flow.name),
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
