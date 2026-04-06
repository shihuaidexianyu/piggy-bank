package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog

private sealed interface EditAccountDialog {
    data object Name : EditAccountDialog
    data object ArchiveConfirm : EditAccountDialog
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
            EditAccountEffect.Saved, EditAccountEffect.Archived -> onBack()
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
                    onValueChange = { nameDraft = it },
                    onConfirm = {
                        viewModel.updateName(nameDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            EditAccountDialog.ArchiveConfirm -> {
                MoneyConfirmDialog(
                    title = "归档账户",
                    message = "归档后，这个账户会移到已归档列表。",
                    onConfirm = {
                        dialog = null
                        viewModel.archive()
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "确认归档",
                )
            }
        }
    }

    AccountSettingsPickerDialog(
        picker = picker,
        groupType = state.groupType,
        reminderConfig = state.reminderConfig,
        onDismiss = { picker = null },
        onGroupTypeSelected = viewModel::updateGroupType,
        onReminderWeekdaySelected = viewModel::updateReminderWeekday,
        onReminderTimeSelected = viewModel::updateReminderTime,
    )

    MoneyFormPage(
        title = "账户管理",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = "账户信息") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "账户名称",
                    trailing = state.name,
                    modifier = Modifier.clickable {
                        nameDraft = state.name
                        dialog = EditAccountDialog.Name
                    },
                )
            }
        }
        item {
            AccountTypeReminderListSection(
                groupType = state.groupType,
                reminderConfig = state.reminderConfig,
                onAccountTypeClick = { picker = AccountSettingsPicker.ACCOUNT_TYPE },
                onReminderWeekdayClick = { picker = AccountSettingsPicker.REMINDER_WEEKDAY },
                onReminderTimeClick = { picker = AccountSettingsPicker.REMINDER_TIME },
            )
        }
        item {
            MoneyCard {
                MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, enabled = !state.isLoading)
            }
        }
        item { MoneySectionHeader(title = "归档") }
        item {
            MoneyListSection {
                MoneyListRow(
                    title = "归档账户",
                    subtitle = "移到已归档列表",
                    showChevron = false,
                    modifier = Modifier.clickable { dialog = EditAccountDialog.ArchiveConfirm },
                )
            }
        }
    }
}

