package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                    isArchived = state.isArchived,
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
                if (state.isArchived) {
                    MoneyStatusPill(text = "已归档", accent = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "提醒时间 ${state.reminderConfig.displayText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isStale && !state.isArchived) {
                    MoneyStatusPill(text = "待核对", accent = MaterialTheme.colorScheme.secondary)
                }
                if (!state.isArchived) {
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
    val amountColor = when {
        record.amount > 0 -> moneyColors.income
        record.amount < 0 -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val amountText = AmountFormatter.format(record.amount, settings)
    MoneyListRow(
        title = record.title,
        subtitle = DateTimeTextFormatter.format(record.occurredAt),
        trailing = amountText,
        showChevron = false,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${record.title}，$amountText，${DateTimeTextFormatter.format(record.occurredAt)}"
            role = Role.Button
        },
    )
}
