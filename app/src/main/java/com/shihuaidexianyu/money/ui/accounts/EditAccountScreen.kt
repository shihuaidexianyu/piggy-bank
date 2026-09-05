package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
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
import com.shihuaidexianyu.money.domain.model.AccountKind
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
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect

private sealed interface EditAccountDialog {
    data object CloseConfirm : EditAccountDialog
    data class KindSwitch(val target: AccountKind) : EditAccountDialog
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
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    val hiddenDoneMessage = stringResource(R.string.account_hidden_done)
    val unhiddenDoneMessage = stringResource(R.string.account_unhidden_done)
    val undoLabel = stringResource(R.string.action_undo)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        when (effect) {
            EditAccountEffect.Saved, EditAccountEffect.AccountClosed -> onBack()
            EditAccountEffect.Closed -> onClosed()
            is EditAccountEffect.HiddenChanged -> if (effect.hidden) {
                rootDispatcher?.dispatch(
                    rootSnackbarEffect(
                        message = hiddenDoneMessage,
                        actionLabel = undoLabel,
                        action = RootSnackbarAction.UnhideAccount(effect.accountId),
                    ),
                )
            } else {
                rootDispatcher?.dispatch(rootSnackbarEffect(message = unhiddenDoneMessage))
            }
            else -> {}
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            is EditAccountDialog.KindSwitch -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.account_kind_switch_title),
                    message = stringResource(R.string.account_kind_switch_message),
                    onConfirm = {
                        viewModel.updateKind(currentDialog.target)
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
                    destructive = true,
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
        onBack = guardedBack,
        footer = {
            if (!state.isLoading && !state.isClosed) {
                MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, enabled = !state.isLoading)
            }
        },
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "edit-account"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        item { MoneySectionHeader(title = stringResource(R.string.account_information)) }
        item {
            // Inline name editing matches the create page; dirty tracking still flows through
            // viewModel.updateName, so the discard-on-back guard keeps working.
            MoneyCard {
                MoneySingleLineField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = stringResource(R.string.account_name),
                    enabled = !state.isClosed && !state.isSaving,
                )
            }
        }
        item {
            MoneyListSection {
                if (!state.isClosed) {
                    AccountVisualListRows(
                        colorName = state.colorName,
                        iconName = state.iconName,
                        onColorClick = { picker = AccountSettingsPicker.COLOR },
                        onIconClick = { picker = AccountSettingsPicker.ICON },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.account_kind_title),
                        subtitle = if (state.kind == AccountKind.INVESTMENT) {
                            stringResource(R.string.account_kind_investment_hint)
                        } else {
                            null
                        },
                        showChevron = false,
                        accessory = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccountKind.entries.forEach { option ->
                                    FilterChip(
                                        selected = state.kind == option,
                                        enabled = !state.isSaving,
                                        onClick = {
                                            if (option != state.kind) {
                                                dialog = EditAccountDialog.KindSwitch(option)
                                            }
                                        },
                                        label = { Text(accountKindLabel(option)) },
                                    )
                                }
                            }
                        },
                    )
                    MoneySectionDivider()
                    MoneyListRow(
                        title = stringResource(R.string.account_hide),
                        subtitle = stringResource(R.string.account_hide_description),
                        showChevron = false,
                        switchChecked = state.isHidden,
                        onClick = {
                            if (!state.isUpdatingHidden && !state.isSaving) {
                                viewModel.setHidden(!state.isHidden)
                            }
                        },
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
                    onReminderEnabledChange = viewModel::setReminderEnabled,
                    onReminderPeriodClick = { picker = AccountSettingsPicker.REMINDER_PERIOD },
                    onReminderWeekdayClick = { picker = AccountSettingsPicker.REMINDER_WEEKDAY },
                    onReminderMonthDayClick = { picker = AccountSettingsPicker.REMINDER_MONTH_DAY },
                    onReminderTimeClick = { picker = AccountSettingsPicker.REMINDER_TIME },
                )
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
                        enabled = state.canClose,
                        onClick = {
                            dialog = EditAccountDialog.CloseConfirm
                        },
                    )
                }
            }
        }
    }
}
