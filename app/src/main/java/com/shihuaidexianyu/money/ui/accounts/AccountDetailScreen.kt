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
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUiEffects(effectFlow, snackbarHostState) { }
    MoneyFormPage(
        title = state.name.ifEmpty { stringResource(R.string.account_detail_title) },
        trailing = if (state.isMissing || state.isLoading || state.loadErrorMessage != null || state.isClosed) null else {
            { TextButton(onClick = onManageAccount) { Text(stringResource(R.string.accounts_management)) } }
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
                val closure = accountClosurePresentation(state.isClosed, state.currentBalance)
                if (state.isClosed) {
                    MoneyStatusPill(text = closure.statusText, accent = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(
                        text = stringResource(
                            R.string.account_detail_reminder_time_format,
                            state.reminderConfig.displayText,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isStale && !state.isClosed) {
                    MoneyStatusPill(
                        text = stringResource(R.string.account_stale_badge),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        if (state.canMutateLedger()) {
            item { MoneySectionHeader(title = stringResource(R.string.account_detail_quick_actions)) }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = stringResource(R.string.account_detail_record_income),
                        subtitle = stringResource(R.string.account_detail_preselected),
                        modifier = Modifier.clickable(onClick = onRecordIncome),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.account_detail_record_expense),
                        subtitle = stringResource(R.string.account_detail_preselected),
                        modifier = Modifier.clickable(onClick = onRecordExpense),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.history_transfer),
                        subtitle = if (state.openAccountCount >= 2) {
                            stringResource(R.string.account_detail_transfer_from)
                        } else {
                            stringResource(R.string.account_detail_transfer_unavailable)
                        },
                        modifier = Modifier.clickable(
                            enabled = state.openAccountCount >= 2,
                            onClick = onRecordTransfer,
                        ),
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.account_detail_reconcile),
                        subtitle = stringResource(R.string.account_detail_reconcile_description),
                        modifier = Modifier.clickable(onClick = onStartUpdateBalance),
                    )
                }
            }
        }
        // === This month summary ===
        item {
            MoneySectionHeader(title = stringResource(R.string.account_detail_month_cash))
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
            }
        }
        // === Recent records ===
        if (state.recentRecords.isNotEmpty()) {
            item {
                MoneySectionHeader(title = stringResource(R.string.account_detail_recent_records))
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
        AccountDetailRecordKind.CASH_FLOW -> stringResource(R.string.history_cash_flow)
        AccountDetailRecordKind.TRANSFER -> stringResource(R.string.history_transfer)
        AccountDetailRecordKind.BALANCE_UPDATE -> stringResource(R.string.account_detail_kind_reconciliation)
        AccountDetailRecordKind.BALANCE_ADJUSTMENT -> stringResource(R.string.account_detail_kind_adjustment)
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
