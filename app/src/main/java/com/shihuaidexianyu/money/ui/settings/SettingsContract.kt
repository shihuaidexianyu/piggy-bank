package com.shihuaidexianyu.money.ui.settings

import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.data.backup.ImportReceipt

data class SettingsSectionContract(
    val title: String,
    val itemKeys: List<String>,
)

val SETTINGS_SECTION_CONTRACTS: List<SettingsSectionContract> = listOf(
    SettingsSectionContract("显示", listOf("theme", "amount_color", "currency_symbol", "mask_in_app")),
    SettingsSectionContract("隐私", listOf("biometric", "relock", "hide_recents", "hide_widget", "hide_notification")),
    SettingsSectionContract("通知", listOf("permission_channels", "reminder_management", "account_reminder_config")),
    SettingsSectionContract("数据", listOf("plaintext_warning", "export_json", "import_preview", "receipt_history")),
    SettingsSectionContract("关于", listOf("version", "offline_data_safety")),
)

data class ImportReceiptHistoryRow(
    val receipt: ImportReceipt,
    val canRollback: Boolean,
)

fun importReceiptHistoryRows(
    receipts: List<ImportReceipt>,
    rollbackEligibleReceiptId: String?,
): List<ImportReceiptHistoryRow> = receipts.map { receipt ->
        ImportReceiptHistoryRow(
            receipt = receipt,
            canRollback = receipt.id == rollbackEligibleReceiptId,
        )
    }

fun notificationChannelStatus(enabled: Boolean): String = if (enabled) "已开启" else "已关闭"

const val SETTINGS_PLAINTEXT_EXPORT_WARNING: String = "未加密 JSON，仅保存到可信位置"

const val SETTINGS_ABOUT_DATA_SAFETY_COPY: String =
    "完全离线运行；账本仅保存在本机，系统自动备份和设备迁移均已关闭。" +
        "手动导出是未加密 JSON，请自行保存到可信位置。"

enum class NotificationSettingsAction {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
}

data class NotificationSettingsPresentation(
    val status: String,
    val action: NotificationSettingsAction,
    val settingsTarget: NotificationSettingsTarget? = null,
)

fun notificationSettingsPresentation(
    state: NotificationPermissionUiState,
): NotificationSettingsPresentation = when (state) {
    NotificationPermissionUiState.Granted -> NotificationSettingsPresentation(
        status = "权限和渠道均可用",
        action = NotificationSettingsAction.OPEN_SETTINGS,
        settingsTarget = NotificationSettingsTarget.APPLICATION,
    )

    NotificationPermissionUiState.NotRequested -> NotificationSettingsPresentation(
        status = "未授权",
        action = NotificationSettingsAction.REQUEST_PERMISSION,
    )

    is NotificationPermissionUiState.Denied -> if (state.canRequestAgain) {
        NotificationSettingsPresentation(
            status = "已拒绝，可再次申请",
            action = NotificationSettingsAction.REQUEST_PERMISSION,
        )
    } else {
        NotificationSettingsPresentation(
            status = "已拒绝，请前往系统设置",
            action = NotificationSettingsAction.OPEN_SETTINGS,
            settingsTarget = NotificationSettingsTarget.APPLICATION,
        )
    }

    is NotificationPermissionUiState.SettingsRequired -> NotificationSettingsPresentation(
        status = when (state.target) {
            NotificationSettingsTarget.APPLICATION -> "应用通知已关闭"
            NotificationSettingsTarget.RECURRING_CHANNEL -> "提醒通知渠道已关闭"
            NotificationSettingsTarget.BALANCE_CHANNEL -> "核对通知渠道已关闭"
        },
        action = NotificationSettingsAction.OPEN_SETTINGS,
        settingsTarget = state.target,
    )
}
