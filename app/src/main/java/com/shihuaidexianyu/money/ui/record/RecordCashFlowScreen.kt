package com.shihuaidexianyu.money.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
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
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.ui.common.MoneyAmountHeroField
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInsetDivider
import com.shihuaidexianyu.money.ui.common.MoneyInsetGroup
import com.shihuaidexianyu.money.ui.common.MoneyInsetRow
import com.shihuaidexianyu.money.ui.common.MoneyInsetTextRow
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun RecordCashFlowScreen(
    viewModel: RecordCashFlowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)
    val moneyColors = LocalMoneyColors.current

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) {}
    state.pendingTerminal?.let { terminal ->
        LaunchedEffect(terminal.token) {
            if (terminal.kind == FormTerminalKind.SAVED) onSaved()
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
        title = state.direction.displayName,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "record-cash"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item {
            MoneyAmountHeroField(
                value = state.amountText,
                label = stringResource(R.string.field_amount),
                accent = if (state.direction == CashFlowDirection.INFLOW) {
                    moneyColors.income
                } else {
                    moneyColors.expense
                },
                onValueChange = viewModel::updateAmount,
                isError = state.amountError != null,
                supportingText = state.amountError,
                autoOpenKeypad = true,
            )
        }
        if (state.noteSuggestions.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    items(state.noteSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.applyNoteSuggestion(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
        item {
            MoneyInsetGroup {
                MoneyInsetRow(
                    label = stringResource(R.string.account_single),
                    value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                    onClick = { showAccountPicker = true },
                    showChevron = true,
                    isError = state.accountError != null,
                    supportingText = state.accountError,
                )
                MoneyInsetDivider()
                MoneyInsetTextRow(
                    label = stringResource(R.string.field_optional_note),
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    isError = state.noteError != null,
                    supportingText = state.noteError,
                )
                MoneyInsetDivider()
                MoneyInsetRow(
                    label = stringResource(R.string.field_date),
                    value = DateTimeTextFormatter.formatDateOnly(state.occurredAtMillis),
                    onClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    isError = state.occurredAtError != null,
                )
                MoneyInsetDivider()
                MoneyInsetRow(
                    label = stringResource(R.string.field_time),
                    value = DateTimeTextFormatter.formatTimeOnly(state.occurredAtMillis),
                    onClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    isError = state.occurredAtError != null,
                    supportingText = state.occurredAtError,
                )
            }
        }
        item {
            MoneySaveButton(
                onClick = { viewModel.save() },
                isSaving = state.isSaving,
                enabled = state.pendingTerminal == null,
            )
        }
    }
}
