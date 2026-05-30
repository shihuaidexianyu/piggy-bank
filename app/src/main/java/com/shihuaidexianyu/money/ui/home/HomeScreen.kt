package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AccountPickerSortMode
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarMessage: String? = null,
    onSnackbarMessageShown: () -> Unit = {},
    onStartCashFlow: (CashFlowDirection, Long) -> Unit,
    onStartTransfer: () -> Unit,
    onStartUpdateBalance: (Long) -> Unit,
    onAllRemindersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerDirection by remember { mutableStateOf<CashFlowDirection?>(null) }
    var showUpdateBalancePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarMessageShown()
        }
    }

    pickerDirection?.let { direction ->
        AccountPickerDialog(
            title = "选择${direction.displayName}账户",
            accounts = state.accountOptions,
            sortMode = when (direction) {
                CashFlowDirection.OUTFLOW -> AccountPickerSortMode.HIGHEST_BALANCE
                CashFlowDirection.INFLOW -> AccountPickerSortMode.MOST_USED
            },
            settings = state.settings,
            onDismiss = { pickerDirection = null },
            onPick = { accountId ->
                pickerDirection = null
                onStartCashFlow(direction, accountId)
            },
        )
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = "选择核对余额账户",
            accounts = state.accountOptions,
            sortMode = AccountPickerSortMode.STALE_FIRST,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }
    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)
        MoneyPageTitle(
            title = "首页",
            trailing = {
                ReminderHeaderButton(
                    dueCount = state.dueReminders.size + state.staleAccountCount,
                    onClick = onAllRemindersClick,
                )
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TotalAssetsBlock(
                    totalAssets = AmountFormatter.format(state.totalAssets, state.settings),
                    accountCount = state.accountOptions.size,
                    staleCount = state.staleAccountCount,
                    showStaleMark = state.settings.showStaleMark,
                )
            }
            item {
                MoneySectionHeader(title = "快速记录")
            }
            item {
                ActionGrid(
                    onInflow = { pickerDirection = CashFlowDirection.INFLOW },
                    onOutflow = { pickerDirection = CashFlowDirection.OUTFLOW },
                    onTransfer = onStartTransfer,
                    onUpdateBalance = { showUpdateBalancePicker = true },
                    enabled = state.accountOptions.isNotEmpty(),
                    transferEnabled = state.accountOptions.size >= 2,
                )
            }
        }
    }
}

@Composable
private fun ReminderHeaderButton(
    dueCount: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = CircleShape,
            ),
    ) {
        BadgedBox(
            badge = {
                if (dueCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = "提醒",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TotalAssetsBlock(
    totalAssets: String,
    accountCount: Int,
    staleCount: Int,
    showStaleMark: Boolean,
) {
    val borderColor = if (staleCount > 0 && showStaleMark) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "总资产",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val statusText = if (staleCount > 0 && showStaleMark) {
                    "$staleCount 个待核对"
                } else {
                    "$accountCount 个账户"
                }
                MoneyStatusPill(
                    text = statusText,
                    accent = if (staleCount > 0 && showStaleMark) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            val amountStyle = when {
                totalAssets.length > 24 -> MaterialTheme.typography.titleLarge
                totalAssets.length > 18 -> MaterialTheme.typography.headlineSmall
                totalAssets.length > 14 -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.displayLarge
            }
            Text(
                text = totalAssets,
                style = amountStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (staleCount > 0 && showStaleMark) {
                Text(
                    text = "有账户余额需要确认，更新后总资产会更准确。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionGrid(
    onInflow: () -> Unit,
    onOutflow: () -> Unit,
    onTransfer: () -> Unit,
    onUpdateBalance: () -> Unit,
    enabled: Boolean,
    transferEnabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(12.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    label = "入账",
                    icon = Icons.Rounded.SouthWest,
                    tint = LocalMoneyColors.current.income,
                    onClick = onInflow,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    label = "出账",
                    icon = Icons.Rounded.NorthEast,
                    tint = LocalMoneyColors.current.expense,
                    onClick = onOutflow,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    label = "转账",
                    icon = Icons.Rounded.SwapHoriz,
                    tint = LocalMoneyColors.current.transfer,
                    onClick = onTransfer,
                    enabled = transferEnabled,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    label = "余额",
                    icon = Icons.Rounded.Sync,
                    tint = LocalMoneyColors.current.current,
                    onClick = onUpdateBalance,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 94.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = tint.copy(alpha = if (enabled) 0.07f else 0.04f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.82f else 0.62f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
