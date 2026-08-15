package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.domain.usecase.calculatePeriodDelta
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun UpdateBalanceScreen(
    viewModel: UpdateBalanceViewModel,
    settings: PortableSettings,
    onShowResult: () -> Unit,
    onStartCashFlow: (CashFlowDirection, Long, Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            if (terminal.kind == FormTerminalKind.SAVED) onShowResult()
            viewModel.ackTerminal(terminal.token)
        }
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = stringResource(R.string.account_choose),
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.updateAccount(it)
                showAccountPicker = false
            },
        )
    }

    MoneyDateTimePickerHost(
        field = dateTimeField,
        currentMillis = state.occurredAtMillis,
        onPick = viewModel::updateOccurredAt,
        onDismiss = { dateTimeField = null },
    )

    MoneyFormPage(
        title = stringResource(R.string.balance_reconcile_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "update-balance"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        val isInvestment = selectedAccount?.isInvestment == true
        item {
            MoneyCard {
                MoneySelectionField(
                    label = stringResource(R.string.account_single),
                    value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                    onClick = { showAccountPicker = true },
                    isError = state.accountError != null,
                    supportingText = state.accountError,
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_system),
                    value = if (state.isLoading) {
                        "—"
                    } else {
                        formatInAppAmount(state.systemBalanceBeforeUpdate, settings)
                    },
                )
                // Freshness matters everywhere: on investment accounts a stale check means
                // stale P&L, on funding accounts it answers "how long since I last checked".
                Text(
                    text = lastCheckedText(selectedAccount?.lastBalanceUpdateAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyAmountField(
                    value = state.actualBalanceText,
                    onValueChange = viewModel::updateActualBalance,
                    label = stringResource(R.string.balance_actual),
                    allowSigned = true,
                    isError = state.actualBalanceError != null,
                    supportingText = state.actualBalanceError,
                    enabled = !state.isSaving,
                    autoOpenKeypad = true,
                )
                if (state.actualBalanceEdited || state.deltaPreview != 0L || state.actualBalancePreview == null) {
                    MoneyTonalButton(
                        onClick = viewModel::resetActualBalanceToSystem,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.balance_set_unchanged))
                    }
                }
                MoneyDateTimeFields(
                    valueMillis = state.occurredAtMillis,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = if (state.timeEdited) {
                        null
                    } else {
                        stringResource(R.string.ledger_default_current_time)
                    },
                    errorText = state.occurredAtError,
                )
            }
        }
        item {
            // Verdict card only: the amounts themselves are already visible in the form card
            // above — repeating them here just made the page read twice as long.
            MoneyCard {
                MoneyInlineLabelValue(
                    label = stringResource(
                        if (isInvestment) R.string.balance_delta_investment else R.string.balance_delta,
                    ),
                    value = state.deltaPreview?.let { formatInAppAmount(it, settings) } ?: "—",
                )
                state.deltaPreview?.let { delta ->
                    Text(
                        text = if (isInvestment) {
                            // On an investment account the difference IS the investment result —
                            // name it, and quantify it against the pre-check value when possible.
                            investmentDeltaText(delta, state.systemBalanceBeforeUpdate)
                        } else {
                            when {
                                delta > 0 -> stringResource(R.string.balance_above_system)
                                delta < 0 -> stringResource(R.string.balance_below_system)
                                else -> stringResource(R.string.balance_unchanged_hint)
                            }
                        },
                        color = when {
                            delta > 0 -> LocalMoneyColors.current.income
                            delta < 0 -> LocalMoneyColors.current.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val nonZeroDelta = state.deltaPreview
                if (nonZeroDelta != null && nonZeroDelta != 0L) {
                    if (isInvestment) {
                        // A difference here is the market, not a bookkeeping error: no
                        // "record the missing income/expense" audit affordances — recording
                        // market movement as cash flow is exactly the mistake to prevent.
                        Text(
                            text = stringResource(R.string.balance_investment_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.balance_correction_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val accountId = state.selectedAccountId
                        if (accountId != null) {
                            val prefillAmount = abs(nonZeroDelta)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Below system balance (negative delta): the most likely missing
                                // entry is an expense, so put that action first as the hint.
                                val outflowFirst = nonZeroDelta < 0L
                                MoneyTonalButton(
                                    onClick = {
                                        onStartCashFlow(
                                            if (outflowFirst) CashFlowDirection.OUTFLOW else CashFlowDirection.INFLOW,
                                            accountId,
                                            prefillAmount,
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            if (outflowFirst) R.string.balance_record_expense else R.string.balance_record_income,
                                        ),
                                    )
                                }
                                MoneyTonalButton(
                                    onClick = {
                                        onStartCashFlow(
                                            if (outflowFirst) CashFlowDirection.INFLOW else CashFlowDirection.OUTFLOW,
                                            accountId,
                                            prefillAmount,
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            if (outflowFirst) R.string.balance_record_income else R.string.balance_record_expense,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    enabled = state.pendingTerminal == null,
                    label = stringResource(
                        when {
                            state.deltaPreview == 0L -> R.string.balance_confirm_unchanged
                            isInvestment -> R.string.balance_save_investment_update
                            else -> R.string.balance_save_reconciliation
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Renders the investment result as a gain/loss label plus a signed percentage suffix. The
 * percentage is measured against the pre-check system balance and omitted when that baseline is
 * not positive (a percentage against zero or a negative value would be meaningless or read
 * backwards).
 */
@Composable
internal fun investmentDeltaText(delta: Long, systemBalance: Long): String {
    if (delta == 0L) return stringResource(R.string.balance_unchanged_hint)
    val base = stringResource(
        if (delta > 0L) R.string.history_investment_gain else R.string.history_investment_loss,
    )
    val percent = calculatePeriodDelta(
        currentAmount = systemBalance + delta,
        baselineAmount = systemBalance,
    ).percentageText ?: return base
    val sign = if (delta > 0L) "+" else "-"
    return "$base · $sign$percent"
}

@Composable
private fun lastCheckedText(lastBalanceUpdateAt: Long?): String {
    if (lastBalanceUpdateAt == null) return stringResource(R.string.balance_never_checked)
    // Calendar days, not rolling 24h windows — a check done yesterday evening must read as
    // yesterday this morning, not as "checked today".
    val zone = ZoneId.systemDefault()
    val lastDate = Instant.ofEpochMilli(lastBalanceUpdateAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(lastDate, today).coerceAtLeast(0L).toInt()
    return if (days == 0) {
        stringResource(R.string.balance_checked_today)
    } else {
        stringResource(R.string.balance_last_checked_days_format, days)
    }
}
