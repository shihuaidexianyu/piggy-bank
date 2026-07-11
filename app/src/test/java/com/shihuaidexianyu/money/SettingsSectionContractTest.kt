package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.reminder.NotificationPermissionUiState
import com.shihuaidexianyu.money.ui.reminder.NotificationSettingsTarget
import com.shihuaidexianyu.money.ui.settings.NotificationSettingsAction
import com.shihuaidexianyu.money.ui.settings.SETTINGS_ABOUT_DATA_SAFETY_COPY
import com.shihuaidexianyu.money.ui.settings.SETTINGS_PLAINTEXT_EXPORT_WARNING
import com.shihuaidexianyu.money.ui.settings.SETTINGS_SECTION_CONTRACTS
import com.shihuaidexianyu.money.ui.settings.notificationSettingsPresentation
import com.shihuaidexianyu.money.ui.settings.importReceiptHistoryRows
import com.shihuaidexianyu.money.ui.settings.notificationChannelStatus
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
                "显示" to listOf("theme", "amount_color", "currency_symbol", "mask_in_app"),
                "隐私" to listOf("biometric", "relock", "hide_recents", "hide_widget", "hide_notification"),
                "通知" to listOf("permission_channels", "reminder_management", "account_reminder_config"),
                "数据" to listOf("plaintext_warning", "export_json", "import_preview", "receipt_history"),
                "关于" to listOf("version", "offline_data_safety"),
            ),
            SETTINGS_SECTION_CONTRACTS.map { it.title to it.itemKeys },
        )
    }

    @Test
    fun `notification status tells the user whether to request or open exact settings`() {
        val notRequested = notificationSettingsPresentation(NotificationPermissionUiState.NotRequested)
        assertEquals("未授权", notRequested.status)
        assertEquals(NotificationSettingsAction.REQUEST_PERMISSION, notRequested.action)

        val denied = notificationSettingsPresentation(NotificationPermissionUiState.Denied(canRequestAgain = false))
        assertEquals("已拒绝，请前往系统设置", denied.status)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, denied.action)
        assertEquals(NotificationSettingsTarget.APPLICATION, denied.settingsTarget)

        val channel = notificationSettingsPresentation(
            NotificationPermissionUiState.SettingsRequired(NotificationSettingsTarget.RECURRING_CHANNEL),
        )
        assertTrue(channel.status.contains("提醒通知渠道"))
        assertEquals(NotificationSettingsTarget.RECURRING_CHANNEL, channel.settingsTarget)

        val granted = notificationSettingsPresentation(NotificationPermissionUiState.Granted)
        assertEquals("权限和渠道均可用", granted.status)
        assertEquals(NotificationSettingsAction.OPEN_SETTINGS, granted.action)
    }

    @Test
    fun `recurring and balance channel rows use independent facts`() {
        assertEquals("已关闭", notificationChannelStatus(enabled = false))
        assertEquals("已开启", notificationChannelStatus(enabled = true))
        assertEquals(
            listOf("已关闭", "已开启"),
            listOf(false, true).map(::notificationChannelStatus),
        )
        assertEquals(
            listOf("已关闭", "已关闭"),
            listOf(false, false).map(::notificationChannelStatus),
        )
        assertEquals(
            listOf("已开启", "已开启"),
            listOf(true, true).map(::notificationChannelStatus),
        )
    }

    @Test
    fun `backup and about copy describe the actual offline plaintext model`() {
        val copy = "$SETTINGS_PLAINTEXT_EXPORT_WARNING $SETTINGS_ABOUT_DATA_SAFETY_COPY"
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
