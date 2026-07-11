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
import androidx.compose.ui.unit.dp
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
    val versionText = remember(context) { context.applicationVersionText() }
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
                context.startActivity(Intent.createChooser(shareIntent, "导出数据"))
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
                    title = "导出明文备份",
                    message = "将生成$SETTINGS_PLAINTEXT_EXPORT_WARNING。",
                    onConfirm = {
                        onExportData()
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "继续导出",
                    dismissLabel = "取消",
                )
            }

            SettingsDialog.ThemeMode -> {
                MoneyChoiceDialog(
                    title = "主题模式",
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
                    title = "金额颜色习惯",
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
                    title = "货币符号",
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it.take(MAX_CURRENCY_SYMBOL_LENGTH) },
                    onConfirm = {
                        onCurrencySymbolChange(currencyDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "保存",
                )
            }

            is SettingsDialog.ImportConfirm -> {
                MoneyConfirmDialog(
                    title = "覆盖导入数据",
                    message = currentDialog.preview.confirmMessage(),
                    onConfirm = {
                        onConfirmImport(currentDialog.stageId)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "确认导入",
                    dismissLabel = "取消",
                )
            }

            SettingsDialog.RelockDelay -> {
                MoneyChoiceDialog(
                    title = "离开后重新锁定",
                    options = AppRelockDelay.entries,
                    selected = devicePreferences.relockDelay,
                    label = { it.displayName() },
                    onSelect = {
                        onRelockDelayChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            is SettingsDialog.ImportSuccess -> {
                MoneyConfirmDialog(
                    title = "导入完成",
                    message = "已保存导入前安全快照。若结果不符合预期，可立即撤销本次导入。",
                    onConfirm = {
                        onRollbackImport(currentDialog.receiptId)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "撤销本次导入",
                    dismissLabel = "完成",
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
        title = "设置",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = SETTINGS_SECTION_CONTRACTS[0].title) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "主题模式",
                    subtitle = "跟随系统，或固定为浅色 / 深色",
                    trailing = devicePreferences.themeMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "金额颜色习惯",
                    subtitle = "统一首页、历史和金额差额的红绿显示",
                    trailing = settings.amountColorMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.AmountColorMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "货币符号",
                    subtitle = "影响金额显示格式，最多 4 个字符",
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = "应用内隐藏金额",
                    checked = devicePreferences.maskAmountsInApp,
                    onCheckedChange = onMaskAmountsInAppChange,
                )
            }
        }

        item { MoneySectionHeader(title = SETTINGS_SECTION_CONTRACTS[1].title) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "生物识别锁定",
                    subtitle = "启动应用时需指纹或面容解锁",
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
                    title = "重新锁定时间",
                    subtitle = "使用单调计时，熄屏会立即锁定",
                    trailing = devicePreferences.relockDelay.displayName(),
                    modifier = Modifier.clickable { dialog = SettingsDialog.RelockDelay },
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = "隐藏最近任务内容",
                    checked = devicePreferences.hideRecentTasks,
                    onCheckedChange = onHideRecentTasksChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = "隐藏桌面小组件金额",
                    checked = devicePreferences.hideWidgetAmounts,
                    onCheckedChange = onHideWidgetAmountsChange,
                )
                MoneySectionDivider()
                PrivacySwitchRow(
                    title = "隐藏通知金额",
                    checked = devicePreferences.hideNotificationAmounts,
                    onCheckedChange = onHideNotificationAmountsChange,
                )
            }
        }

        item { MoneySectionHeader(title = SETTINGS_SECTION_CONTRACTS[2].title) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "通知权限与渠道",
                    subtitle = notificationPresentation.status,
                    trailing = if (notificationPresentation.action == NotificationSettingsAction.REQUEST_PERMISSION) {
                        "申请"
                    } else {
                        "设置"
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
                    title = "提醒通知渠道",
                    subtitle = "到期收支提醒",
                    trailing = notificationChannelStatus(recurringNotificationChannelEnabled),
                    modifier = Modifier.clickable {
                        onOpenNotificationSettings(NotificationSettingsTarget.RECURRING_CHANNEL)
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "核对通知渠道",
                    subtitle = "账户余额待核对提醒",
                    trailing = notificationChannelStatus(balanceNotificationChannelEnabled),
                    modifier = Modifier.clickable {
                        onOpenNotificationSettings(NotificationSettingsTarget.BALANCE_CHANNEL)
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "收支提醒管理",
                    subtitle = "创建、暂停或处理周期提醒",
                    modifier = Modifier.clickable(onClick = onManageReminders),
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "账户核对提醒配置",
                    subtitle = "进入账户管理，为每个开放账户设置核对周期",
                    modifier = Modifier.clickable(onClick = onManageAccountReminderConfigs),
                )
            }
        }

        item { MoneySectionHeader(title = SETTINGS_SECTION_CONTRACTS[3].title) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "备份格式",
                    subtitle = SETTINGS_PLAINTEXT_EXPORT_WARNING,
                    trailing = "JSON",
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "导出数据",
                    subtitle = SETTINGS_PLAINTEXT_EXPORT_WARNING,
                    trailing = if (state.isExporting) "导出中" else "JSON",
                    modifier = Modifier.clickable(
                        enabled = !state.isExporting && !state.isImporting,
                        onClick = { dialog = SettingsDialog.ExportWarning },
                    ),
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "导入数据",
                    subtitle = "从 JSON 备份覆盖恢复当前数据",
                    trailing = if (state.isImporting) "导入中" else "JSON",
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
                        title = "导入记录",
                        subtitle = "正在校验当前账本与安全快照…",
                        trailing = "加载中",
                        showChevron = false,
                    )
                } else if (state.importHistoryErrorMessage != null) {
                    MoneyListRow(
                        title = "导入记录加载失败",
                        subtitle = state.importHistoryErrorMessage,
                        trailing = "重试",
                        modifier = Modifier.clickable(onClick = onRetryImportHistory),
                    )
                } else if (importReceiptRows.isEmpty()) {
                    MoneyListRow(
                        title = "导入记录",
                        subtitle = "暂无可撤销的导入或恢复记录",
                        showChevron = false,
                    )
                } else {
                    importReceiptRows.forEachIndexed { index, row ->
                        val receipt = row.receipt
                        MoneyListRow(
                            title = if (receipt.kind == ImportReceiptKind.IMPORT) "导入记录" else "恢复记录",
                            subtitle = receipt.historySubtitle(),
                            trailing = if (row.canRollback) "撤销" else "历史",
                            modifier = Modifier.clickable(
                                enabled = row.canRollback && !state.isImporting && !state.isExporting,
                                onClick = { onRollbackImport(receipt.id) },
                            ),
                        )
                        if (index != importReceiptRows.lastIndex) MoneySectionDivider()
                    }
                }
            }
        }

        item { MoneySectionHeader(title = SETTINGS_SECTION_CONTRACTS[4].title) }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "版本",
                    trailing = versionText,
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "离线与数据安全",
                    subtitle = SETTINGS_ABOUT_DATA_SAFETY_COPY,
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

private fun AppRelockDelay.displayName(): String = when (this) {
    AppRelockDelay.IMMEDIATELY -> "立即"
    AppRelockDelay.THIRTY_SECONDS -> "30 秒"
    AppRelockDelay.ONE_MINUTE -> "1 分钟"
    AppRelockDelay.FIVE_MINUTES -> "5 分钟"
}

private fun ImportReceipt.historySubtitle(): String {
    val ledgerCount = counts.cashFlowCount + counts.transferCount +
        counts.balanceUpdateCount + counts.balanceAdjustmentCount
    return "${DateTimeTextFormatter.format(importedAt)} · 账户 ${counts.accountCount} · 记录 $ledgerCount · v$schemaVersion"
}

private fun Context.applicationVersionText(): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    "${info.versionName ?: "未知"} (${info.longVersionCode})"
}.getOrDefault("未知")

private fun BackupValidationResult.confirmMessage(): String {
    return """
        将使用所选 JSON 备份覆盖当前所有账户、流水、余额记录、提醒和可迁移设置。主题、生物识别和金额遮罩等本机设置不会改变。

        导入前会自动生成一份当前数据备份。

        备份内容：账户 ${accountCount} 个，现金流水 ${cashFlowCount} 条，转账 ${transferCount} 条，对账记录 ${balanceUpdateCount} 条，余额调整 ${balanceAdjustmentCount} 条，提醒 ${reminderCount} 条。

        确认继续？
    """.trimIndent()
}
