package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
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
                val moneyColors = LocalMoneyColors.current
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_record_income),
                            icon = IncomeActionIcon,
                            iconTint = moneyColors.income,
                            onClick = onRecordIncome,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_record_expense),
                            icon = ExpenseActionIcon,
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
                            icon = TransferActionIcon,
                            iconTint = moneyColors.transfer,
                            onClick = onRecordTransfer,
                            enabled = state.openAccountCount >= 2,
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionButton(
                            label = stringResource(R.string.account_detail_reconcile),
                            icon = ReconcileActionIcon,
                            iconTint = moneyColors.current,
                            onClick = onStartUpdateBalance,
                            modifier = Modifier.weight(1f),
                        )
                    }
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

/**
 * Hand-drawn geometric action glyphs (24x24) in the same visual language as the account
 * patterns: income drops into a tray, expense leaves it, transfer crosses, reconcile checks.
 * Single-color paths; the button supplies the semantic tint.
 */
private val IncomeActionIcon: ImageVector = actionGlyph {
    line(4f, 19f, 20f, 19f)
    line(12f, 6f, 12f, 15f)
    line(7f, 10f, 12f, 15f)
    line(17f, 10f, 12f, 15f)
}

private val ExpenseActionIcon: ImageVector = actionGlyph {
    line(4f, 5f, 20f, 5f)
    line(12f, 18f, 12f, 9f)
    line(7f, 13f, 12f, 8f)
    line(17f, 13f, 12f, 8f)
}

private val TransferActionIcon: ImageVector = actionGlyph {
    line(4f, 8f, 16f, 8f)
    line(12f, 4f, 17f, 8f)
    line(12f, 12f, 17f, 8f)
    line(20f, 16f, 8f, 16f)
    line(12f, 12f, 7f, 16f)
    line(12f, 20f, 7f, 16f)
}

private val ReconcileActionIcon: ImageVector = actionGlyph {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(21f, 12f)
        arcTo(9f, 9f, 0f, true, true, 3f, 12f)
        arcTo(9f, 9f, 0f, true, true, 21f, 12f)
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(7.5f, 12.5f)
        lineTo(10.5f, 15.5f)
        lineTo(16.5f, 9f)
    }
}

private fun actionGlyph(draw: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(draw).build()

private fun ImageVector.Builder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }
}

/**
 * Neutral tonal button with a semantic icon tint. The container stays stock Material 3 (no filled
 * color blocks); semantic colors appear only on the small icon, keeping the grid quiet.
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
    MoneyTonalButton(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        enabled = enabled,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
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
