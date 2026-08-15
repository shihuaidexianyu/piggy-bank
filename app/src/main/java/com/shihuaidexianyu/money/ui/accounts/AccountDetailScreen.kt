package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shihuaidexianyu.money.ui.common.RecordKindBadge
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.BalanceTransitionText
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    effectFlow: SharedFlow<AccountDetailEffect>,
    onManageAccount: () -> Unit,
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
    // Same row language as the history ledger (V1): type badge, compact-time subtitle (the
    // account is the page itself), signed amount, and this account's before → after balance.
    val amountText = signedFormatInAppAmount(record.amount, settings)
    val timeLabel = DateTimeTextFormatter.formatCompactDayTime(record.occurredAt, System.currentTimeMillis())
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
    // Zero-delta checks confirm a balance without moving it: keep the single confirmed value
    // ("余额 ¥x") instead of a "x → x" transition. Every other row shows this account's
    // before → after pair (transfers included — the viewer-relative leg only).
    val isZeroDeltaCheck = record.kind == AccountDetailRecordKind.BALANCE_UPDATE && record.amount == 0L
    val zeroDeltaBalanceText = if (isZeroDeltaCheck) {
        record.balanceAfter?.let { balance ->
            stringResource(R.string.account_picker_balance_format, formatInAppAmount(balance, settings))
        }
    } else {
        null
    }
    val recordContentDescription = buildString {
        append(
            stringResource(
                R.string.account_detail_record_semantics_format,
                record.title,
                kindLabel,
                amountText,
                DateTimeTextFormatter.format(record.occurredAt),
            ),
        )
        when {
            isZeroDeltaCheck -> record.balanceAfter?.let { balance ->
                append(
                    stringResource(
                        R.string.account_detail_balance_confirmed_semantics_format,
                        formatInAppAmount(balance, settings),
                    ),
                )
            }
            record.balanceBefore != null && record.balanceAfter != null -> append(
                stringResource(
                    R.string.history_balance_change_semantics_format,
                    formatInAppAmount(record.balanceBefore, settings),
                    formatInAppAmount(record.balanceAfter, settings),
                ),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = recordContentDescription
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecordKindBadge(
            kind = record.kind.toHistoryRecordKind(),
            amount = record.amount,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Same shared-line layout as the history row: title+amount, then time and balance.
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                when {
                    zeroDeltaBalanceText != null -> Text(
                        text = zeroDeltaBalanceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    record.balanceBefore != null && record.balanceAfter != null -> BalanceTransitionText(
                        before = record.balanceBefore,
                        after = record.balanceAfter,
                        settings = settings,
                    )
                }
            }
        }
    }
}

private fun AccountDetailRecordKind.toHistoryRecordKind(): HistoryRecordKind = when (this) {
    AccountDetailRecordKind.CASH_FLOW -> HistoryRecordKind.CASH_FLOW
    AccountDetailRecordKind.TRANSFER -> HistoryRecordKind.TRANSFER
    AccountDetailRecordKind.BALANCE_UPDATE -> HistoryRecordKind.BALANCE_UPDATE
    AccountDetailRecordKind.BALANCE_ADJUSTMENT -> HistoryRecordKind.BALANCE_ADJUSTMENT
}
