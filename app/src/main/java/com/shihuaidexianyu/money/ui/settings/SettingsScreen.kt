package com.shihuaidexianyu.money.ui.settings

import android.content.ClipData
import android.content.Intent
import android.net.Uri
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
import com.shihuaidexianyu.money.domain.model.MAX_CURRENCY_SYMBOL_LENGTH
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
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

private sealed interface SettingsDialog {
    data object ExportWarning : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object AmountColorMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
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
    onManageAccountOrder: () -> Unit,
    onCreateSavingsGoal: () -> Unit,
    onExportData: () -> Unit,
    onImportData: (Uri) -> Unit,
    onConfirmImport: (String) -> Unit,
    onRollbackImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.portableSettings
    val devicePreferences = state.devicePreferences
    val context = LocalContext.current
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
                    message = "将生成未加密 JSON，仅保存到可信位置。",
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

    MoneyFormPage(
        title = "设置",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item { MoneySectionHeader(title = "显示") }
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
            }
        }

        item { MoneySectionHeader(title = "数据") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "导出数据",
                    subtitle = "未加密 JSON，仅保存到可信位置",
                    trailing = if (state.isExporting) "导出中" else "JSON",
                    modifier = Modifier.clickable(
                        enabled = !state.isExporting && !state.isImporting,
                        onClick = { dialog = SettingsDialog.ExportWarning },
                    ),
                )
                state.importHistory.firstOrNull()?.let { receipt ->
                    MoneySectionDivider()
                    MoneyListRow(
                        title = "撤销最近一次导入",
                        subtitle = "使用已验证的导入前安全快照恢复",
                        trailing = "撤销",
                        modifier = Modifier.clickable(
                            enabled = !state.isImporting && !state.isExporting,
                            onClick = { onRollbackImport(receipt.id) },
                        ),
                    )
                }
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
            }
        }

        item { MoneySectionHeader(title = "账户管理") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "账户顺序",
                    subtitle = "调整账户页和选择器里的展示顺序",
                    trailing = "自定义",
                    modifier = Modifier.clickable(onClick = onManageAccountOrder),
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "储蓄目标",
                    subtitle = "添加或管理储蓄目标",
                    trailing = "添加",
                    modifier = Modifier.clickable(onClick = onCreateSavingsGoal),
                )
            }
        }
    }
}

private fun BackupValidationResult.confirmMessage(): String {
    return """
        将使用所选 JSON 备份覆盖当前所有账户、流水、余额记录、提醒和可迁移设置。主题、生物识别和金额遮罩等本机设置不会改变。

        导入前会自动生成一份当前数据备份。

        备份内容：账户 ${accountCount} 个，现金流水 ${cashFlowCount} 条，转账 ${transferCount} 条，对账记录 ${balanceUpdateCount} 条，余额调整 ${balanceAdjustmentCount} 条，提醒 ${reminderCount} 条。

        确认继续？
    """.trimIndent()
}
