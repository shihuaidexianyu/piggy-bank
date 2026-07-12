package com.shihuaidexianyu.money.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.MAX_CURRENCY_SYMBOL_LENGTH
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog
import kotlinx.coroutines.flow.SharedFlow
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

private sealed interface SettingsDialog {
    data object ExportWarning : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object AmountColorMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
    data object RelockDelay : SettingsDialog
    data class ImportConfirm(
        val preview: BackupValidationResult,
        val stageId: String,
    ) : SettingsDialog
    data class ImportSuccess(val receiptId: String) : SettingsDialog
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    effectFlow: SharedFlow<SettingsEffect>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmountColorModeChange: (AmountColorMode) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onRelockDelayChange: (AppRelockDelay) -> Unit,
    onMaskAmountsInAppChange: (Boolean) -> Unit,
    onHideWidgetAmountsChange: (Boolean) -> Unit,
    onHideNotificationAmountsChange: (Boolean) -> Unit,
    onHideRecentTasksChange: (Boolean) -> Unit,
    notificationPermissionState: NotificationPermissionUiState,
    recurringNotificationChannelEnabled: Boolean,
    balanceNotificationChannelEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: (NotificationSettingsTarget) -> Unit,
    onManageReminders: () -> Unit,
    onManageAccountReminderConfigs: () -> Unit,
    onExportData: () -> Unit,
    onImportData: (Uri) -> Unit,
    onConfirmImport: (String) -> Unit,
    onRollbackImport: (String) -> Unit,
    onRetryImportHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.portableSettings
    val devicePreferences = state.devicePreferences
    val context = LocalContext.current
    val versionInfo = remember(context) { context.applicationVersionInfo() }
    val unknownLabel = stringResource(R.string.settings_unknown)
    val versionText = versionInfo?.let {
        stringResource(R.string.settings_version_format, it.name ?: unknownLabel, it.code)
    } ?: unknownLabel
    val exportChooserTitle = stringResource(R.string.settings_export_chooser)
    val relockDelayLabels = mapOf(
        AppRelockDelay.IMMEDIATELY to stringResource(R.string.settings_relock_immediately),
        AppRelockDelay.THIRTY_SECONDS to stringResource(R.string.settings_relock_30_seconds),
        AppRelockDelay.ONE_MINUTE to stringResource(R.string.settings_relock_1_minute),
        AppRelockDelay.FIVE_MINUTES to stringResource(R.string.settings_relock_5_minutes),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportData)
    }

    CollectUiEffects(effectFlow, snackbarHostState) { effect ->
        when (effect) {
            is SettingsEffect.ExportReady -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = effect.mimeType
                    putExtra(Intent.EXTRA_STREAM, effect.uri)
                    putExtra(Intent.EXTRA_TITLE, effect.fileName)
                    putExtra(Intent.EXTRA_SUBJECT, effect.fileName)
                    clipData = ClipData.newUri(context.contentResolver, effect.fileName, effect.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, exportChooserTitle))
            }

            is SettingsEffect.ImportPreviewReady -> {
                dialog = SettingsDialog.ImportConfirm(
                    preview = effect.preview,
                    stageId = effect.stageId,
                )
            }

            is SettingsEffect.ImportFinished -> {
                dialog = SettingsDialog.ImportSuccess(effect.receipt.id)
            }

            is SettingsEffect.RollbackFinished -> Unit
            is SettingsEffect.ShowMessage -> Unit
        }
    }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            SettingsDialog.ExportWarning -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_export_plaintext_title),
                    message = stringResource(R.string.settings_plaintext_export_message),
                    onConfirm = {
                        onExportData()
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.settings_continue_export),
                    dismissLabel = stringResource(R.string.action_cancel),
                )
            }

            SettingsDialog.ThemeMode -> {
                MoneyChoiceDialog(
                    title = stringResource(R.string.settings_theme_mode),
                    options = ThemeMode.entries,
                    selected = devicePreferences.themeMode,
                    label = { it.displayName },
                    onSelect = {
                        onThemeModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.AmountColorMode -> {
                MoneyChoiceDialog(
                    title = stringResource(R.string.settings_amount_color),
                    options = AmountColorMode.entries,
                    selected = settings.amountColorMode,
                    label = { it.displayName },
                    onSelect = {
                        onAmountColorModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.CurrencySymbol -> {
                MoneyTextInputDialog(
                    title = stringResource(R.string.settings_currency_symbol),
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it.take(MAX_CURRENCY_SYMBOL_LENGTH) },
                    onConfirm = {
                        onCurrencySymbolChange(currencyDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.action_save),
                )
            }

            is SettingsDialog.ImportConfirm -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_import_overwrite_title),
                    message = currentDialog.preview.confirmMessage(),
                    onConfirm = {
                        onConfirmImport(currentDialog.stageId)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.settings_confirm_import),
                    dismissLabel = stringResource(R.string.action_cancel),
                )
            }

            SettingsDialog.RelockDelay -> {
                MoneyChoiceDialog(
                    title = stringResource(R.string.settings_relock_title),
                    options = AppRelockDelay.entries,
                    selected = devicePreferences.relockDelay,
                    label = { relockDelayLabels.getValue(it) },
                    onSelect = {
                        onRelockDelayChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            is SettingsDialog.ImportSuccess -> {
                MoneyConfirmDialog(
                    title = stringResource(R.string.settings_import_complete),
                    message = stringResource(R.string.settings_import_complete_message),
                    onConfirm = {
                        onRollbackImport(currentDialog.receiptId)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = stringResource(R.string.settings_rollback_import),
                    dismissLabel = stringResource(R.string.action_done),
                )
            }
        }
    }

    val notificationPresentation = notificationSettingsPresentation(notificationPermissionState)
    val importReceiptRows = importReceiptHistoryRows(
        receipts = state.importHistory,
        rollbackEligibleReceiptId = state.rollbackEligibleReceiptId,
    )
    MoneyFormPage(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[0].titleRes)) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = stringResource(R.string.settings_theme_description),
                    trailing = devicePreferences.themeMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_amount_color),
                    subtitle = stringResource(R.string.settings_amount_color_description),
                    trailing = settings.amountColorMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.AmountColorMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_currency_symbol),
                    subtitle = stringResource(R.string.settings_currency_description),
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_mask_in_app),
                    checked = devicePreferences.maskAmountsInApp,
                    onCheckedChange = onMaskAmountsInAppChange,
                )
            }
        }

        item { MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[1].titleRes)) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = stringResource(R.string.settings_biometric_lock),
                    subtitle = stringResource(R.string.settings_biometric_description),
                    showChevron = false,
                    accessory = {
                        Switch(
                            checked = devicePreferences.biometricLock,
                            onCheckedChange = onBiometricLockChange,
                        )
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_relock_time),
                    subtitle = stringResource(R.string.settings_relock_description),
                    trailing = relockDelayLabels.getValue(devicePreferences.relockDelay),
                    modifier = Modifier.clickable { dialog = SettingsDialog.RelockDelay },
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_recents),
                    checked = devicePreferences.hideRecentTasks,
                    onCheckedChange = onHideRecentTasksChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_widget),
                    checked = devicePreferences.hideWidgetAmounts,
                    onCheckedChange = onHideWidgetAmountsChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = stringResource(R.string.settings_hide_notifications),
                    checked = devicePreferences.hideNotificationAmounts,
                    onCheckedChange = onHideNotificationAmountsChange,
                )
            }
        }

        item { MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[2].titleRes)) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = stringResource(R.string.settings_notification_permission_channels),
                    subtitle = stringResource(notificationPresentation.statusRes),
                    trailing = if (notificationPresentation.action == NotificationSettingsAction.REQUEST_PERMISSION) {
                        stringResource(R.string.settings_request_permission)
                    } else {
                        stringResource(R.string.settings_open_system_settings)
                    },
                    modifier = Modifier.clickable {
                        when (notificationPresentation.action) {
                            NotificationSettingsAction.REQUEST_PERMISSION -> onRequestNotificationPermission()
                            NotificationSettingsAction.OPEN_SETTINGS -> onOpenNotificationSettings(
                                notificationPresentation.settingsTarget ?: NotificationSettingsTarget.APPLICATION,
                            )
                        }
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_recurring_channel),
                    subtitle = stringResource(R.string.settings_recurring_channel_description),
                    trailing = stringResource(notificationChannelStatusRes(recurringNotificationChannelEnabled)),
                    modifier = Modifier.clickable {
                        onOpenNotificationSettings(NotificationSettingsTarget.RECURRING_CHANNEL)
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_balance_channel),
                    subtitle = stringResource(R.string.settings_balance_channel_description),
                    trailing = stringResource(notificationChannelStatusRes(balanceNotificationChannelEnabled)),
                    modifier = Modifier.clickable {
                        onOpenNotificationSettings(NotificationSettingsTarget.BALANCE_CHANNEL)
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_reminder_management),
                    subtitle = stringResource(R.string.settings_reminder_management_description),
                    modifier = Modifier.clickable(onClick = onManageReminders),
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_account_reminder_config),
                    subtitle = stringResource(R.string.settings_account_reminder_description),
                    modifier = Modifier.clickable(onClick = onManageAccountReminderConfigs),
                )
            }
        }

        item { MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[3].titleRes)) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = stringResource(R.string.settings_backup_format),
                    subtitle = stringResource(R.string.settings_plaintext_warning),
                    trailing = "JSON",
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_export_data),
                    subtitle = stringResource(R.string.settings_plaintext_warning),
                    trailing = if (state.isExporting) stringResource(R.string.settings_exporting) else "JSON",
                    modifier = Modifier.clickable(
                        enabled = !state.isExporting && !state.isImporting,
                        onClick = { dialog = SettingsDialog.ExportWarning },
                    ),
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_import_data),
                    subtitle = stringResource(R.string.settings_import_description),
                    trailing = if (state.isImporting) stringResource(R.string.settings_importing) else "JSON",
                    modifier = Modifier.clickable(
                        enabled = !state.isImporting && !state.isExporting,
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                    ),
                )
                MoneySectionDivider()
                if (state.isLoadingImportHistory) {
                    MoneyListRow(
                        title = stringResource(R.string.settings_import_history),
                        subtitle = stringResource(R.string.settings_import_history_checking),
                        trailing = stringResource(R.string.settings_loading),
                        showChevron = false,
                    )
                } else if (state.importHistoryErrorMessage != null) {
                    MoneyListRow(
                        title = stringResource(R.string.settings_import_history_error),
                        subtitle = state.importHistoryErrorMessage,
                        trailing = stringResource(R.string.action_retry),
                        modifier = Modifier.clickable(onClick = onRetryImportHistory),
                    )
                } else if (importReceiptRows.isEmpty()) {
                    MoneyListRow(
                        title = stringResource(R.string.settings_import_history),
                        subtitle = stringResource(R.string.settings_import_history_empty),
                        showChevron = false,
                    )
                } else {
                    importReceiptRows.forEachIndexed { index, row ->
                        val receipt = row.receipt
                        MoneyListRow(
                            title = stringResource(
                                if (receipt.kind == ImportReceiptKind.IMPORT) {
                                    R.string.settings_import_history
                                } else {
                                    R.string.settings_restore_history
                                },
                            ),
                            subtitle = receipt.historySubtitle(),
                            trailing = stringResource(
                                if (row.canRollback) R.string.settings_rollback else R.string.settings_history,
                            ),
                            modifier = if (row.canRollback) {
                                Modifier.clickable(
                                    enabled = !state.isImporting && !state.isExporting,
                                    onClick = { onRollbackImport(receipt.id) },
                                )
                            } else {
                                Modifier
                            },
                            showChevron = row.canRollback,
                            isClickable = row.canRollback,
                        )
                        if (index != importReceiptRows.lastIndex) MoneySectionDivider()
                    }
                }
            }
        }

        item { MoneySectionHeader(title = stringResource(SETTINGS_SECTION_CONTRACTS[4].titleRes)) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = stringResource(R.string.settings_version),
                    trailing = versionText,
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = stringResource(R.string.settings_offline_safety),
                    subtitle = stringResource(R.string.settings_offline_safety_copy),
                    showChevron = false,
                )
            }
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MoneyListRow(
        title = title,
        showChevron = false,
        accessory = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun ImportReceipt.historySubtitle(): String {
    val ledgerCount = counts.cashFlowCount + counts.transferCount +
        counts.balanceUpdateCount + counts.balanceAdjustmentCount
    return stringResource(
        R.string.settings_receipt_summary_format,
        DateTimeTextFormatter.format(importedAt),
        counts.accountCount,
        ledgerCount,
        schemaVersion,
    )
}

private data class ApplicationVersionInfo(val name: String?, val code: Long)

private fun Context.applicationVersionInfo(): ApplicationVersionInfo? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    ApplicationVersionInfo(info.versionName, info.longVersionCode)
}.getOrNull()

@Composable
private fun BackupValidationResult.confirmMessage(): String = stringResource(
    R.string.settings_import_confirm_message,
    accountCount,
    cashFlowCount,
    transferCount,
    balanceUpdateCount,
    balanceAdjustmentCount,
    reminderCount,
)
