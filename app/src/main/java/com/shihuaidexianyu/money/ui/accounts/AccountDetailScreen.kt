package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    effectFlow: SharedFlow<AccountDetailEffect>,
    onManageAccount: () -> Unit,
    onRecordIncome: () -> Unit,
    onRecordExpense: () -> Unit,
    onRecordTransfer: () -> Unit,
    onStartUpdateBalance: () -> Unit,
    onReopenAccount: () -> Unit,
    onBackToAccounts: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUiEffects(effectFlow, snackbarHostState) { }
    MoneyFormPage(
        title = state.name.ifEmpty { "账户详情" },
        trailing = if (state.isMissing || state.isLoading || state.loadErrorMessage != null || state.isClosed) null else {
            { TextButton(onClick = onManageAccount) { Text("管理") } }
        },
        onBack = onBackToAccounts,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "account-detail"),
                    onRetry = onRetry,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
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
                    text = formatInAppAmount(state.currentBalance, state.settings),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = state.lastBalanceUpdateAt?.let {
                        "最近核对 ${DateTimeTextFormatter.format(it)}"
                    } ?: "尚未核对余额",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val closure = accountClosurePresentation(state.isClosed, state.currentBalance)
                if (state.isClosed) {
                    MoneyStatusPill(text = closure.statusText, accent = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.currentBalance != 0L) {
                        Text(
                            text = "这个账户来自旧版本且关闭时仍有余额。请重新开启并结清，历史数据不会被改写。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onReopenAccount,
                        enabled = !state.isReopening,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isReopening) "正在重新开启…" else "重新开启账户")
                    }
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
            }
        }
        if (state.canMutateLedger()) {
            item { MoneySectionHeader(title = "快捷记账") }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = "记录收入",
                        subtitle = "已预选当前账户",
                        modifier = Modifier.clickable(onClick = onRecordIncome),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "记录支出",
                        subtitle = "已预选当前账户",
                        modifier = Modifier.clickable(onClick = onRecordExpense),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "转账",
                        subtitle = if (state.openAccountCount >= 2) {
                            "当前账户作为转出账户"
                        } else {
                            "至少需要两个开放账户才能转账"
                        },
                        modifier = Modifier.clickable(
                            enabled = state.openAccountCount >= 2,
                            onClick = onRecordTransfer,
                        ),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "核对余额",
                        subtitle = "按当前账面余额开始核对",
                        modifier = Modifier.clickable(onClick = onStartUpdateBalance),
                    )
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
                            text = formatInAppAmount(state.monthInflow, state.settings),
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
                            text = formatInAppAmount(state.monthOutflow, state.settings),
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
    settings: com.shihuaidexianyu.money.domain.model.PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    val accent = when (record.kind) {
        AccountDetailRecordKind.CASH_FLOW ->
            if (record.amount > 0) moneyColors.income else moneyColors.expense
        AccountDetailRecordKind.TRANSFER -> moneyColors.transfer
        AccountDetailRecordKind.BALANCE_UPDATE, AccountDetailRecordKind.BALANCE_ADJUSTMENT ->
            moneyColors.current
    }
    val amountText = formatInAppAmount(record.amount, settings)
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
