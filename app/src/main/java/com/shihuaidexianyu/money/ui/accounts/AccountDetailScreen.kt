package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onManageAccount: () -> Unit,
    onStartUpdateBalance: () -> Unit,
    onBackToAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(
                title = state.name.ifEmpty { "账户详情" },
                trailing = if (state.isMissing) {
                    null
                } else {
                    {
                        TextButton(onClick = onManageAccount) {
                            Text("管理")
                        }
                    }
                },
            )
        }
        if (state.isMissing) {
            item {
                MoneyEmptyStateCard(
                    title = "账户不存在",
                    subtitle = "这个账户可能已经失效，或者当前路由里的账户 ID 不可用。",
                    action = {
                        Button(
                            onClick = onBackToAccounts,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("返回账户列表")
                        }
                    },
                )
            }
            return@LazyColumn
        }
        item {
            MoneyCard {
                MoneyStatusPill(text = state.groupType.displayName)
                Text(
                    text = AmountFormatter.format(state.currentBalance, state.settings),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = state.lastBalanceUpdateAt?.let {
                        "最近更新 ${DateTimeTextFormatter.format(it)}"
                    } ?: "尚未更新余额",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "提醒时间 ${state.reminderConfig.displayText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.isStale) {
                    MoneyStatusPill(text = "待更新", accent = MaterialTheme.colorScheme.secondary)
                }
                Button(
                    onClick = onStartUpdateBalance,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("更新余额")
                }
            }
        }
        if (
            state.groupType == com.shihuaidexianyu.money.domain.model.AccountGroupType.INVESTMENT &&
            state.latestSettlement != null
        ) {
            item {
                MoneyCard {
                    MoneySectionHeader(title = "最近结算")
                    Text("盈亏 ${AmountFormatter.format(state.latestSettlement.pnl, state.settings)}")
                    Text("收益率 ${"%.2f".format(state.latestSettlement.returnRate * 100)}%")
                    Text("净转入 ${AmountFormatter.format(state.latestSettlement.netTransferIn, state.settings)}")
                    Text("净转出 ${AmountFormatter.format(state.latestSettlement.netTransferOut, state.settings)}")
                }
            }
        }
    }
}

