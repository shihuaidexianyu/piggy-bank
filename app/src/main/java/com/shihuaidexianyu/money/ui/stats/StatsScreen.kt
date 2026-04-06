package com.shihuaidexianyu.money.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(title = "统计")
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
        item {
            MoneySectionHeader(title = "收支趋势")
        }
        item {
            MoneyCard {
                if (state.cashFlowBars.any { it.inflow > 0 || it.outflow > 0 }) {
                    CashFlowChart(
                        bars = state.cashFlowBars,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                } else {
                    Text(
                        text = "暂无收支数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            MoneySectionHeader(title = "净资产变化")
        }
        item {
            MoneyCard {
                if (state.netAssetPoints.any { it.totalBalance != 0L }) {
                    NetAssetChart(
                        points = state.netAssetPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                } else {
                    Text(
                        text = "暂无资产数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            MoneySectionHeader(title = "账户资产占比")
        }
        item {
            MoneyCard {
                AccountShareChart(
                    shares = state.accountShares,
                    settings = state.settings,
                )
            }
        }
        if (state.investmentPoints.isNotEmpty()) {
            item {
                MoneySectionHeader(title = "投资收益")
            }
            item {
                MoneyCard {
                    InvestmentChart(
                        points = state.investmentPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                }
            }
        }
    }
}
