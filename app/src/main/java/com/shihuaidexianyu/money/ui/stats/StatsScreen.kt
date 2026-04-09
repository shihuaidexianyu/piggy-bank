package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyMetricTile
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCashFlowModeChange: (CashFlowCardMode) -> Unit,
    onCashFlowGranularityChange: (CashFlowGranularity) -> Unit,
    onCashFlowDisplayUnitChange: (CashFlowDisplayUnit) -> Unit,
    onCashFlowDateSelect: (Long) -> Unit,
    onCashFlowShiftPeriod: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(
                title = "统计",
                trailing = {
                    if (!state.isLoading) {
                        MoneyStatusPill(text = "${state.overview.activeAccountCount} 个账户")
                    }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { onPeriodChange(period) },
                        label = { Text(period.displayName) },
                    )
                }
            }
        }

        if (state.isLoading) {
            item {
                MoneyEmptyStateCard(
                    title = "正在生成统计",
                    subtitle = "正在汇总当前周期的资金变化与资产分布。",
                )
            }
        } else {
            item {
                MoneySectionHeader(title = "本期总览")
            }
            item {
                StatsOverviewCard(state)
            }

            item {
                MoneySectionHeader(title = "现金流", trailing = "不含转账与余额修正")
            }
            item {
                CashFlowCalendarCard(
                    settings = state.settings,
                    events = state.cashFlowEvents,
                    mode = state.cashFlowCardMode,
                    granularity = state.cashFlowGranularity,
                    displayUnit = state.cashFlowDisplayUnit,
                    selectedEpochDay = state.cashFlowSelectedEpochDay,
                    visibleEpochDay = state.cashFlowVisibleEpochDay,
                    onModeChange = onCashFlowModeChange,
                    onGranularityChange = onCashFlowGranularityChange,
                    onDisplayUnitChange = onCashFlowDisplayUnitChange,
                    onDateSelect = onCashFlowDateSelect,
                    onShiftPeriod = onCashFlowShiftPeriod,
                )
            }

            item {
                MoneySectionHeader(title = "净资产趋势", trailing = "含转账、修正与余额锚点")
            }
            item {
                MoneyCard {
                    MoneyInlineLabelValue(
                        label = "期末净资产",
                        value = AmountFormatter.format(state.overview.currentNetAssets, state.settings),
                    )
                    MoneyInlineLabelValue(
                        label = "较期初变化",
                        value = AmountFormatter.format(state.overview.netAssetDelta, state.settings),
                    )
                    if (state.netAssetPoints.any { it.totalBalance != 0L }) {
                        NetAssetChart(
                            points = state.netAssetPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                    } else {
                        EmptyChartBody("当前周期没有可绘制的资产变化点。")
                    }
                }
            }

            item {
                MoneySectionHeader(title = "资产配置", trailing = "按账户组聚合")
            }
            item {
                MoneyCard {
                    AccountShareChart(
                        groupShares = state.assetGroupShares,
                        topAccounts = state.topAccountShares,
                        settings = state.settings,
                    )
                }
            }

            if (state.overview.activeInvestmentAccountCount > 0) {
                item {
                    MoneySectionHeader(title = "投资结算", trailing = "按结算区间聚合")
                }
                item {
                    MoneyCard {
                        MoneyInlineLabelValue(
                            label = "累计盈亏",
                            value = AmountFormatter.format(state.investmentOverview.totalPnl, state.settings),
                        )
                        MoneyInlineLabelValue(
                            label = "加权收益率",
                            value = formatRate(state.investmentOverview.weightedReturnRate),
                        )
                        MoneyInlineLabelValue(
                            label = "净转入 / 净转出",
                            value = "${AmountFormatter.format(state.investmentOverview.netTransferIn, state.settings)} / ${
                                AmountFormatter.format(state.investmentOverview.netTransferOut, state.settings)
                            }",
                        )
                        if (state.investmentPoints.isNotEmpty()) {
                            InvestmentChart(
                                points = state.investmentPoints,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                        } else {
                            EmptyChartBody("当前周期还没有投资账户结算记录。")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewCard(
    state: StatsUiState,
) {
    val inflowColor = Color(0xFF1D8F5A)
    val outflowColor = Color(0xFFC53C32)
    val netColor = if (state.overview.netCashFlow >= 0L) inflowColor else outflowColor
    val assetColor = if (state.overview.netAssetDelta >= 0L) Color(0xFF2563EB) else outflowColor

    MoneyCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyMetricTile(
                label = "总入账",
                value = AmountFormatter.format(state.overview.totalInflow, state.settings),
                accent = inflowColor,
                modifier = Modifier.weight(1f),
            )
            MoneyMetricTile(
                label = "总出账",
                value = AmountFormatter.format(state.overview.totalOutflow, state.settings),
                accent = outflowColor,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyMetricTile(
                label = "净现金流",
                value = AmountFormatter.format(state.overview.netCashFlow, state.settings),
                accent = netColor,
                modifier = Modifier.weight(1f),
            )
            MoneyMetricTile(
                label = "净资产变化",
                value = AmountFormatter.format(state.overview.netAssetDelta, state.settings),
                accent = assetColor,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "净现金流不含转账与余额修正；净资产趋势会纳入账户之间调拨和余额更新锚点。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChartBody(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "补充记录后，这里会自动按当前周期生成图表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatRate(value: Double?): String {
    return if (value == null) {
        "--"
    } else {
        "${"%.2f".format(value * 100)}%"
    }
}
