package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
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
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyStatusPill
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
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
    onViewAllHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUiEffects(effectFlow, snackbarHostState) { }
    MoneyFormPage(
        title = state.name.ifEmpty { stringResource(R.string.account_detail_title) },
        trailing = if (state.isMissing || state.isLoading || state.loadErrorMessageRes != null || state.isClosed) null else {
            { TextButton(onClick = onManageAccount) { Text(stringResource(R.string.accounts_management)) } }
        },
        onBack = onBackToAccounts,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "account-detail"),
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
                    title = stringResource(R.string.account_detail_missing),
                    subtitle = stringResource(R.string.account_detail_missing_description),
                    action = {
                        Button(
                            onClick = onBackToAccounts,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.account_detail_back_to_list))
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
                        stringResource(
                            R.string.account_detail_last_reconciled_format,
                            DateTimeTextFormatter.format(it),
                        )
                    } ?: stringResource(R.string.account_detail_never_reconciled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Kind tag: the kind reinterprets reconciliation deltas at read time, so the
                // detail header states it explicitly instead of implying it from the list.
                MoneyStatusPill(
                    text = stringResource(
                        if (state.isInvestment) R.string.account_kind_investment else R.string.account_kind_funding,
                    ),
                    accent = if (state.isInvestment) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val closure = accountClosurePresentation(state.isClosed, state.currentBalance)
                if (state.isClosed) {
                    MoneyStatusPill(text = stringResource(closure.statusTextRes), accent = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.currentBalance != 0L) {
                        Text(
                            text = stringResource(R.string.account_detail_legacy_closure_issue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onReopenAccount,
                        enabled = !state.isReopening,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.isReopening) {
                                    R.string.account_detail_reopening
                                } else {
                                    R.string.account_detail_reopen
                                },
                            ),
                        )
                    }
                } else {
                    // The reminder schedule line only makes sense while the reminder is enabled.
                    if (state.reminderConfig.isEnabled) {
                        Text(
                            text = stringResource(
                                R.string.account_detail_reminder_time_format,
                                state.reminderConfig.displayText,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.isStale && !state.isClosed) {
                    MoneyStatusPill(
                        text = stringResource(R.string.account_stale_badge),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (state.isHidden && !state.isClosed) {
                    MoneyStatusPill(
                        text = stringResource(R.string.account_status_hidden),
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.canMutateLedger()) {
            item { MoneySectionHeader(title = stringResource(R.string.account_detail_quick_actions)) }
            item {
                val moneyColors = LocalMoneyColors.current
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_record_income),
                            icon = Icons.Rounded.AddCircle,
                            iconTint = moneyColors.income,
                            onClick = onRecordIncome,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_record_expense),
                            icon = Icons.Rounded.RemoveCircle,
                            iconTint = moneyColors.expense,
                            onClick = onRecordExpense,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickActionButton(
                            label = stringResource(R.string.history_transfer),
                            icon = Icons.Rounded.SwapHoriz,
                            iconTint = moneyColors.transfer,
                            onClick = onRecordTransfer,
                            enabled = state.openAccountCount >= 2,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_reconcile),
                            icon = Icons.Rounded.CheckCircle,
                            iconTint = moneyColors.current,
                            onClick = onStartUpdateBalance,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (state.openAccountCount < 2) {
                    Text(
                        text = stringResource(R.string.account_detail_transfer_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // === This month summary ===
        item {
            MoneySectionHeader(
                title = stringResource(
                    if (state.isInvestment) R.string.account_detail_month_cash_flow else R.string.account_detail_month_cash,
                ),
            )
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
                            text = stringResource(R.string.ledger_income),
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
                            text = stringResource(R.string.ledger_expense),
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
                if (state.isInvestment) {
                    MoneySectionDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.account_detail_month_investment_pnl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = signedFormatInAppAmount(state.monthInvestmentDelta, state.settings),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (state.monthInvestmentDelta >= 0L) {
                                moneyColors.income
                            } else {
                                moneyColors.expense
                            },
                        )
                    }
                }
            }
        }
        // === Recent records ===
        if (state.recentRecords.isNotEmpty()) {
            item {
                MoneySectionHeader(
                    title = stringResource(R.string.account_detail_recent_records),
                    trailingContent = {
                        TextButton(onClick = onViewAllHistory) {
                            Text(stringResource(R.string.account_detail_view_all))
                        }
                    },
                )
            }
            item {
                MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    state.recentRecords.forEachIndexed { index, record ->
                        RecentRecordRow(
                            record = record,
                            settings = state.settings,
                            isInvestment = state.isInvestment,
                        )
                        if (index != state.recentRecords.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular quick action: a tonal circle carries the semantic-tinted icon, with the label below —
 * the same circular language as the home header icons, no filled rectangle blocks.
 */
@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun RecentRecordRow(
    record: AccountDetailRecentRecord,
    settings: com.shihuaidexianyu.money.domain.model.PortableSettings,
    isInvestment: Boolean,
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
        AccountDetailRecordKind.CASH_FLOW -> stringResource(R.string.history_cash_flow)
        AccountDetailRecordKind.TRANSFER -> stringResource(
            if (record.amount < 0L) R.string.transfer_out else R.string.transfer_in,
        )
        AccountDetailRecordKind.BALANCE_UPDATE -> stringResource(
            when {
                record.amount == 0L -> R.string.history_balance_update
                isInvestment && record.amount > 0L -> R.string.history_investment_gain
                isInvestment -> R.string.history_investment_loss
                else -> R.string.history_reconciliation_adjustment
            },
        )
        AccountDetailRecordKind.BALANCE_ADJUSTMENT -> stringResource(R.string.history_balance_adjustment)
    }
    val recordContentDescription = stringResource(
        R.string.account_detail_record_semantics_format,
        record.title,
        kindLabel,
        amountText,
        DateTimeTextFormatter.format(record.occurredAt),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = recordContentDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp),
            color = accent,
            shape = CircleShape,
            content = {},
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
