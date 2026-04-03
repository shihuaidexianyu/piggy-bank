package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyMetricTile
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartCashFlow: (CashFlowDirection, Long) -> Unit,
    onStartTransfer: () -> Unit,
    onStartUpdateBalance: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerDirection by remember { mutableStateOf<CashFlowDirection?>(null) }
    var showUpdateBalancePicker by remember { mutableStateOf(false) }

    pickerDirection?.let { direction ->
        AccountPickerDialog(
            title = "选择${direction.displayName}账户",
            accounts = state.accountOptions,
            onDismiss = { pickerDirection = null },
            onPick = { accountId ->
                pickerDirection = null
                onStartCashFlow(direction, accountId)
            },
        )
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = "选择更新余额账户",
            accounts = state.accountOptions,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            MoneyPageTitle(title = "首页")
        }
        item {
            MoneyCard {
                Text("总资产", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = AmountFormatter.format(state.totalAssets, state.settings),
                    style = MaterialTheme.typography.displayLarge,
                )
                val statusText = if (state.staleAccountCount > 0 && state.settings.showStaleMark) {
                    "${state.staleAccountCount} 个账户待更新"
                } else {
                    "${state.groupSections.sumOf { it.accounts.size }} 个账户"
                }
                MoneyStatusPill(
                    text = statusText,
                    accent = if (state.staleAccountCount > 0 && state.settings.showStaleMark) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyMetricTile(
                    label = "${state.settings.homePeriod.displayName}净流入",
                    value = AmountFormatter.format(state.periodNetInflow, state.settings),
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFFC24A4A),
                )
                MoneyMetricTile(
                    label = "${state.settings.homePeriod.displayName}净流出",
                    value = AmountFormatter.format(state.periodNetOutflow, state.settings),
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFF3F8A63),
                )
            }
        }
        item {
            MoneySectionHeader(title = "快捷操作")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionButton(
                    label = "入账",
                    icon = { Icon(Icons.Outlined.SouthWest, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { pickerDirection = CashFlowDirection.INFLOW },
                    enabled = state.accountOptions.isNotEmpty(),
                )
                HomeActionButton(
                    label = "出账",
                    icon = { Icon(Icons.Outlined.NorthEast, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { pickerDirection = CashFlowDirection.OUTFLOW },
                    enabled = state.accountOptions.isNotEmpty(),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionButton(
                    label = "转账",
                    icon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = onStartTransfer,
                    enabled = state.accountOptions.size >= 2,
                )
                HomeActionButton(
                    label = "更新余额",
                    icon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { showUpdateBalancePicker = true },
                    enabled = state.accountOptions.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        icon()
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
