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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    title = "账户名称",
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
                    title = "关闭账户",
                    message = "仅当余额为 0 时可以关闭；关闭后账户将只读并保留历史记录。",
                    onConfirm = {
                        dialog = null
                        viewModel.closeAccount()
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "确认关闭",
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
        title = "账户管理",
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
        item { MoneySectionHeader(title = "账户信息") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "账户名称",
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
                        title = "隐藏账户",
                        subtitle = "仍计入净资产和分析，可在选择器中展开使用",
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
                        text = "这个账户已关闭，仅保留历史记录和余额查看。",
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
            item { MoneySectionHeader(title = "关闭") }
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = "关闭账户",
                        subtitle = if (state.canClose) {
                            "关闭后只读并保留历史"
                        } else {
                            "请先结清余额（当前余额不为 0）"
                        },
                        showChevron = false,
                        modifier = Modifier.clickable(enabled = state.canClose) {
                            dialog = EditAccountDialog.CloseConfirm
                        },
                    )
                }
            }
        }
    }
}
