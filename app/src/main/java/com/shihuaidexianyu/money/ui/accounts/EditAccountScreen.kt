package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog

private sealed interface EditAccountDialog {
    data object Name : EditAccountDialog
    data object CloseConfirm : EditAccountDialog
}

@Composable
fun EditAccountScreen(
    viewModel: EditAccountViewModel,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<EditAccountDialog?>(null) }
    var picker by remember { mutableStateOf<AccountSettingsPicker?>(null) }
    var nameDraft by remember(state.name) { mutableStateOf(state.name) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        when (effect) {
            EditAccountEffect.Saved, EditAccountEffect.AccountClosed -> onBack()
            EditAccountEffect.Closed -> onClosed()
            else -> {}
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            EditAccountDialog.Name -> {
                MoneyTextInputDialog(
                    title = stringResource(R.string.account_name),
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(MAX_ACCOUNT_NAME_LENGTH) },
                    onConfirm = {
                        viewModel.updateName(nameDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            EditAccountDialog.CloseConfirm -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.account_close_title),
                    message = stringResource(R.string.account_close_message),
                    onConfirm = {
                        dialog = null
                        viewModel.closeAccount()
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.account_confirm_close),
                )
            }
        }
    }

    AccountSettingsPickerDialog(
        picker = if (state.isClosed) null else picker,
        colorName = state.colorName,
        iconName = state.iconName,
        reminderConfig = state.reminderConfig,
        onDismiss = { picker = null },
        onColorSelected = viewModel::updateColorName,
        onIconSelected = viewModel::updateIconName,
        onReminderPeriodSelected = viewModel::updateReminderPeriod,
        onReminderWeekdaySelected = viewModel::updateReminderWeekday,
        onReminderMonthDaySelected = viewModel::updateReminderMonthDay,
        onReminderTimeSelected = viewModel::updateReminderTime,
    )

    MoneyFormPage(
        title = stringResource(R.string.account_management_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "edit-account"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item { MoneySectionHeader(title = stringResource(R.string.account_information)) }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = stringResource(R.string.account_name),
                    trailing = state.name,
                    showChevron = !state.isClosed,
                    modifier = Modifier.clickable(enabled = !state.isClosed) {
                        nameDraft = state.name
                        dialog = EditAccountDialog.Name
                    },
                )
                if (!state.isClosed) {
                    AccountVisualListRows(
                        colorName = state.colorName,
                        iconName = state.iconName,
                        onColorClick = { picker = AccountSettingsPicker.COLOR },
                        onIconClick = { picker = AccountSettingsPicker.ICON },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.account_hide),
                        subtitle = stringResource(R.string.account_hide_description),
                        showChevron = false,
                        accessory = {
                            Switch(
                                checked = state.isHidden,
                                onCheckedChange = viewModel::setHidden,
                                enabled = !state.isUpdatingHidden && !state.isSaving,
                            )
                        },
                    )
                }
            }
        }
        if (state.isClosed) {
            item {
                MoneyCard {
                    Text(
                        text = stringResource(R.string.account_closed_readonly_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                AccountReminderListSection(
                    reminderConfig = state.reminderConfig,
                    onReminderPeriodClick = { picker = AccountSettingsPicker.REMINDER_PERIOD },
                    onReminderWeekdayClick = { picker = AccountSettingsPicker.REMINDER_WEEKDAY },
                    onReminderMonthDayClick = { picker = AccountSettingsPicker.REMINDER_MONTH_DAY },
                    onReminderTimeClick = { picker = AccountSettingsPicker.REMINDER_TIME },
                )
            }
            item {
                MoneyCard {
                    MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, enabled = !state.isLoading)
                }
            }
            item { MoneySectionHeader(title = stringResource(R.string.account_close_section)) }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = stringResource(R.string.account_close_title),
                        subtitle = if (state.canClose) {
                            stringResource(R.string.account_close_available_description)
                        } else {
                            stringResource(R.string.account_close_nonzero_description)
                        },
                        showChevron = false,
                        isClickable = state.canClose,
                        modifier = Modifier.clickable(enabled = state.canClose) {
                            dialog = EditAccountDialog.CloseConfirm
                        },
                    )
                }
            }
        }
    }
}
