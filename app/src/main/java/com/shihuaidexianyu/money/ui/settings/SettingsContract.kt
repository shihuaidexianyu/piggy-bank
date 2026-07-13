package com.shihuaidexianyu.money.ui.settings

import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.data.backup.ImportReceipt

data class SettingsSectionContract(
    @param:StringRes val titleRes: Int,
    val itemKeys: List<String>,
)

val SETTINGS_SECTION_CONTRACTS: List<SettingsSectionContract> = listOf(
    SettingsSectionContract(
        R.string.settings_section_display,
        listOf("theme", "amount_color", "currency_symbol", "account_order", "mask_in_app"),
    ),
    SettingsSectionContract(R.string.settings_section_privacy, listOf("biometric", "relock", "hide_recents", "hide_widget", "hide_notification")),
    SettingsSectionContract(R.string.settings_section_notifications, listOf("permission_channels", "reminder_management", "account_reminder_config")),
    SettingsSectionContract(R.string.settings_section_data, listOf("plaintext_warning", "export_json", "import_preview", "receipt_history")),
    SettingsSectionContract(R.string.settings_section_about, listOf("version", "offline_data_safety")),
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

@StringRes
fun notificationChannelStatusRes(enabled: Boolean): Int = if (enabled) {
    R.string.settings_channel_enabled
} else {
    R.string.settings_channel_disabled
}

enum class NotificationSettingsAction {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
}

data class NotificationSettingsPresentation(
    @param:StringRes val statusRes: Int,
    val action: NotificationSettingsAction,
    val settingsTarget: NotificationSettingsTarget? = null,
)

fun notificationSettingsPresentation(
    state: NotificationPermissionUiState,
): NotificationSettingsPresentation = when (state) {
    NotificationPermissionUiState.Granted -> NotificationSettingsPresentation(
        statusRes = R.string.settings_notifications_available,
        action = NotificationSettingsAction.OPEN_SETTINGS,
        settingsTarget = NotificationSettingsTarget.APPLICATION,
    )

    NotificationPermissionUiState.NotRequested -> NotificationSettingsPresentation(
        statusRes = R.string.settings_notifications_not_authorized,
        action = NotificationSettingsAction.REQUEST_PERMISSION,
    )

    is NotificationPermissionUiState.Denied -> if (state.canRequestAgain) {
        NotificationSettingsPresentation(
            statusRes = R.string.settings_notifications_denied_retry,
            action = NotificationSettingsAction.REQUEST_PERMISSION,
        )
    } else {
        NotificationSettingsPresentation(
            statusRes = R.string.settings_notifications_denied_settings,
            action = NotificationSettingsAction.OPEN_SETTINGS,
            settingsTarget = NotificationSettingsTarget.APPLICATION,
        )
    }

    is NotificationPermissionUiState.SettingsRequired -> NotificationSettingsPresentation(
        statusRes = when (state.target) {
            NotificationSettingsTarget.APPLICATION -> R.string.settings_app_notifications_disabled
            NotificationSettingsTarget.RECURRING_CHANNEL -> R.string.settings_recurring_channel_disabled
            NotificationSettingsTarget.BALANCE_CHANNEL -> R.string.settings_balance_channel_disabled
        },
        action = NotificationSettingsAction.OPEN_SETTINGS,
        settingsTarget = state.target,
    )
}
