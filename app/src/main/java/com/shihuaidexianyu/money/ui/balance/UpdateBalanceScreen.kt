package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.clickable
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
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "update-balance"),
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
                    modifier = Modifier.clickable { showAccountPicker = true },
                    isError = state.accountError != null,
                    supportingText = state.accountError,
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_system),
                    value = formatInAppAmount(state.systemBalanceBeforeUpdate, settings),
                )
                if (isInvestment) {
                    // Freshness matters on investment accounts: the value drifts daily without
                    // any record existing, so an old check means stale P&L and net worth.
                    Text(
                        text = lastCheckedText(selectedAccount?.lastBalanceUpdateAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyAmountField(
                    value = state.actualBalanceText,
                    onValueChange = viewModel::updateActualBalance,
                    label = stringResource(R.string.balance_actual),
                    allowSigned = true,
                    isError = state.actualBalanceError != null,
                    supportingText = state.actualBalanceError,
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
                    timeSubtitle = stringResource(R.string.ledger_default_current_time),
                    errorText = state.occurredAtError,
                )
            }
        }
        item {
            MoneyCard {
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_system),
                    value = formatInAppAmount(state.systemBalanceBeforeUpdate, settings),
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_actual),
                    value = state.actualBalancePreview?.let { formatInAppAmount(it, settings) } ?: "-",
                )
                MoneyInlineLabelValue(
                    label = stringResource(
                        if (isInvestment) R.string.balance_delta_investment else R.string.balance_delta,
                    ),
                    value = state.deltaPreview?.let { formatInAppAmount(it, settings) } ?: "-",
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
                                MoneyTonalButton(
                                    onClick = {
                                        onStartCashFlow(
                                            CashFlowDirection.INFLOW,
                                            accountId,
                                            prefillAmount,
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.balance_record_income))
                                }
                                MoneyTonalButton(
                                    onClick = {
                                        onStartCashFlow(
                                            CashFlowDirection.OUTFLOW,
                                            accountId,
                                            prefillAmount,
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.balance_record_expense))
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
private fun investmentDeltaText(delta: Long, systemBalance: Long): String {
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
    val days = ((System.currentTimeMillis() - lastBalanceUpdateAt) / DAY_MILLIS)
        .coerceAtLeast(0L)
        .toInt()
    return if (days == 0) {
        stringResource(R.string.balance_checked_today)
    } else {
        stringResource(R.string.balance_last_checked_days_format, days)
    }
}

private const val DAY_MILLIS = 86_400_000L
