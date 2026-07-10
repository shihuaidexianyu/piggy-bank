package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.usecase.AccountDetailRecordKind
import com.shihuaidexianyu.money.domain.usecase.AccountDetailRecentRecord
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
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
    MoneyFormPage(
        title = state.name.ifEmpty { "账户详情" },
        trailing = if (state.isMissing) null else {
            { TextButton(onClick = onManageAccount) { Text("管理") } }
        },
        onBack = onBackToAccounts,
        modifier = modifier,
    ) {
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
            return@MoneyFormPage
        }
        // === Balance card ===
        item {
            MoneyCard {
                AccountIconBadge(
                    iconName = state.iconName,
                    colorName = state.colorName,
                    isClosed = state.isClosed,
                )
                Text(
                    text = AmountFormatter.format(state.currentBalance, state.settings),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = state.lastBalanceUpdateAt?.let {
                        "最近核对 ${DateTimeTextFormatter.format(it)}"
                    } ?: "尚未核对余额",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.isClosed) {
                    MoneyStatusPill(text = "已关闭", accent = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "提醒时间 ${state.reminderConfig.displayText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isStale && !state.isClosed) {
                    MoneyStatusPill(text = "待核对", accent = MaterialTheme.colorScheme.secondary)
                }
                if (!state.isClosed) {
                    Button(
                        onClick = onStartUpdateBalance,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("核对余额")
                    }
                }
            }
        }
        // === This month summary ===
        item {
            MoneySectionHeader(title = "本月收支")
        }
        item {
            MoneyCard {
                val moneyColors = LocalMoneyColors.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "收入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = AmountFormatter.format(state.monthInflow, state.settings),
                            style = MaterialTheme.typography.titleLarge,
                            color = moneyColors.income,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "支出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = AmountFormatter.format(state.monthOutflow, state.settings),
                            style = MaterialTheme.typography.titleLarge,
                            color = moneyColors.expense,
                        )
                    }
                }
            }
        }
        // === Recent records ===
        if (state.recentRecords.isNotEmpty()) {
            item {
                MoneySectionHeader(title = "最近记录")
            }
            item {
                MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    state.recentRecords.forEachIndexed { index, record ->
                        RecentRecordRow(record = record, settings = state.settings)
                        if (index != state.recentRecords.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRecordRow(
    record: AccountDetailRecentRecord,
    settings: com.shihuaidexianyu.money.domain.model.AppSettings,
) {
    val moneyColors = LocalMoneyColors.current
    val accent = when (record.kind) {
        AccountDetailRecordKind.CASH_FLOW ->
            if (record.amount > 0) moneyColors.income else moneyColors.expense
        AccountDetailRecordKind.TRANSFER -> moneyColors.transfer
        AccountDetailRecordKind.BALANCE_UPDATE, AccountDetailRecordKind.BALANCE_ADJUSTMENT ->
            moneyColors.current
    }
    val amountText = AmountFormatter.format(record.amount, settings)
    val kindLabel = when (record.kind) {
        AccountDetailRecordKind.CASH_FLOW -> "收支"
        AccountDetailRecordKind.TRANSFER -> "转账"
        AccountDetailRecordKind.BALANCE_UPDATE -> "对账"
        AccountDetailRecordKind.BALANCE_ADJUSTMENT -> "调整"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${record.title}，$kindLabel，$amountText，${DateTimeTextFormatter.format(record.occurredAt)}"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(color = accent, shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Title + type label
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$kindLabel · ${DateTimeTextFormatter.format(record.occurredAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Amount with direction color
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            maxLines = 1,
        )
    }
}
