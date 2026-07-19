package com.shihuaidexianyu.money.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerHost
import com.shihuaidexianyu.money.ui.common.MoneyDateTimeFields
import com.shihuaidexianyu.money.ui.common.MoneyDateTimePickerField
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySelectionField
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField

@Composable
fun SharePreviewScreen(
    viewModel: SharePreviewViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var dateTimeField by remember { mutableStateOf<MoneyDateTimePickerField?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is SharePreviewEffect.Saved) onSaved()
    }
    if (showAccountPicker) {
        AccountPickerDialog(
            title = stringResource(R.string.share_choose_open_account),
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
        currentMillis = state.occurredAt,
        onPick = viewModel::updateOccurredAt,
        onDismiss = { dateTimeField = null },
    )

    MoneyFormPage(
        title = stringResource(R.string.share_preview_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        item {
            MoneyCard {
                Text(
                    stringResource(
                        if (state.isUncertain) R.string.share_uncertain else R.string.share_confirm_parsed,
                    ),
                )
                Text(state.originalText, maxLines = 4)
                if (state.candidateAmounts.size > 1) {
                    Text(stringResource(R.string.share_multiple_amounts))
                }
            }
        }
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(
                        value = state,
                        isLoading = state.isLoading,
                        errorMessage = state.loadErrorMessage,
                        retryToken = "share-accounts",
                    ),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
        } else item {
            MoneyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CashFlowDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = state.direction == direction,
                            onClick = { viewModel.updateDirection(direction) },
                            label = { Text(direction.displayName) },
                        )
                    }
                }
                MoneyAmountField(
                    value = state.amountText,
                    onValueChange = viewModel::updateAmount,
                )
                if (state.accounts.isEmpty()) {
                    Text(stringResource(R.string.share_no_open_account))
                    Button(onClick = onCreateAccount) { Text(stringResource(R.string.share_create_account)) }
                } else {
                    MoneySelectionField(
                        label = stringResource(R.string.account_single),
                        value = selectedAccount?.name ?: stringResource(R.string.field_please_choose),
                        modifier = Modifier.clickable { showAccountPicker = true },
                    )
                }
                MoneySingleLineField(
                    value = state.note,
                    onValueChange = viewModel::updateNote,
                    label = stringResource(R.string.share_note_label),
                )
                Text("${state.note.length}/200")
                MoneyDateTimeFields(
                    valueMillis = state.occurredAt,
                    onDateClick = { dateTimeField = MoneyDateTimePickerField.DATE },
                    onTimeClick = { dateTimeField = MoneyDateTimePickerField.TIME },
                    timeSubtitle = stringResource(R.string.share_time_description),
                )
                state.fieldError?.let { Text(it) }
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    enabled = !state.isLoading && state.accounts.isNotEmpty(),
                    label = stringResource(R.string.share_confirm_save),
                )
            }
        }
    }
}
