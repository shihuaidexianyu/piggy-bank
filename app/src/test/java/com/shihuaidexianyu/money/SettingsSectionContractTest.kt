package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.settings.NotificationSettingsAction
import com.shihuaidexianyu.money.ui.settings.SETTINGS_SECTION_CONTRACTS
import com.shihuaidexianyu.money.ui.settings.notificationSettingsPresentation
import com.shihuaidexianyu.money.ui.settings.importReceiptHistoryRows
import com.shihuaidexianyu.money.ui.settings.notificationChannelStatusRes
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.ImportReceiptCounts
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceiptStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SettingsSectionContractTest {
    @Test
    fun `only newest committed receipt offers rollback while older receipts remain visible`() {
        val older = receipt("older", importedAt = 100L)
        val latest = receipt("latest", importedAt = 200L)

        val rows = importReceiptHistoryRows(
            receipts = listOf(latest, older),
            rollbackEligibleReceiptId = older.id,
        )

        assertEquals(listOf("latest", "older"), rows.map { it.receipt.id })
        assertFalse(rows.first().canRollback)
        assertTrue(rows.last().canRollback)
    }

    @Test
    fun `settings has exactly five sections with the approved ownership`() {
        assertEquals(
            listOf(
                R.string.settings_section_display to listOf(
                    "theme",
                    "amount_color",
                    "currency_symbol",
                    "account_order",
                    "mask_in_app",
                ),
                R.string.settings_section_privacy to listOf("biometric", "relock", "hide_recents", "hide_widget", "hide_notification"),
                R.string.settings_section_notifications to listOf("permission_channels", "reminder_management", "account_reminder_config"),
                R.string.settings_section_data to listOf("plaintext_warning", "export_json", "import_preview", "receipt_history"),
                R.string.settings_section_about to listOf("version", "offline_data_safety"),
            ),
            SETTINGS_SECTION_CONTRACTS.map { it.titleRes to it.itemKeys },
        )
    }

    @Test
    fun `notification status tells the user whether to request or open exact settings`() {
        val notRequested = notificationSettingsPresentation(NotificationPermissionUiState.NotRequested)
        assertEquals(R.string.settings_notifications_not_authorized, notRequested.statusRes)
        assertEquals(NotificationSettingsAction.REQUEST_PERMISSION, notRequested.action)

        val denied = notificationSettingsPresentation(NotificationPermissionUiState.Denied(canRequestAgain = false))
        assertEquals(R.string.settings_notifications_denied_settings, denied.statusRes)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, denied.action)
        assertEquals(NotificationSettingsTarget.APPLICATION, denied.settingsTarget)

        val channel = notificationSettingsPresentation(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.RECURRING_CHANNEL),
        )
        assertEquals(R.string.settings_recurring_channel_disabled, channel.statusRes)
        assertEquals(NotificationSettingsTarget.RECURRING_CHANNEL, channel.settingsTarget)

        val granted = notificationSettingsPresentation(NotificationPermissionUiState.Granted)
        assertEquals(R.string.settings_notifications_available, granted.statusRes)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, granted.action)
    }

    @Test
    fun `recurring and balance channel rows use independent facts`() {
        assertEquals(R.string.settings_channel_disabled, notificationChannelStatusRes(enabled = false))
        assertEquals(R.string.settings_channel_enabled, notificationChannelStatusRes(enabled = true))
        assertEquals(
            listOf(R.string.settings_channel_disabled, R.string.settings_channel_enabled),
            listOf(false, true).map(::notificationChannelStatusRes),
        )
        assertEquals(
            listOf(R.string.settings_channel_disabled, R.string.settings_channel_disabled),
            listOf(false, false).map(::notificationChannelStatusRes),
        )
        assertEquals(
            listOf(R.string.settings_channel_enabled, R.string.settings_channel_enabled),
            listOf(true, true).map(::notificationChannelStatusRes),
        )
    }

    @Test
    fun `backup and about copy describe the actual offline plaintext model`() {
        val copy = java.io.File("src/main/res/values/strings.xml").readText()
        assertTrue(copy.contains("未加密 JSON"))
        assertTrue(copy.contains("完全离线"))
        assertTrue(copy.contains("系统自动备份"))
        listOf("AES", ".enc", "精确闹钟", "多个目标", "云同步").forEach { forbidden ->
            assertFalse(copy.contains(forbidden), forbidden)
        }
    }

    private fun receipt(id: String, importedAt: Long) = ImportReceipt(
        id = id,
        kind = ImportReceiptKind.IMPORT,
        status = ImportReceiptStatus.COMMITTED,
        importedAt = importedAt,
        sourceFileSha256 = "a".repeat(64),
        targetContentSha256 = "b".repeat(64),
        safetySnapshotFileName = "money-pre-import-$importedAt-test.json",
        safetySnapshotSha256 = "c".repeat(64),
        schemaVersion = 4,
        counts = ImportReceiptCounts(1, 2, 3, 4, 5, 6, 1),
        commitSequence = 0L,
    )
}
