package com.shihuaidexianyu.money

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupPortableSettings
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportJsonFileWriterTest {
    @Test
    fun writeCreatesShareableFileProviderUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = ExportJsonFileWriter(
            context,
            { java.time.ZoneId.of("Asia/Shanghai") },
            BackupJsonCodec,
        )
        val snapshot = emptySnapshot()
        val result = writer.write(
            snapshot = snapshot,
            timestamp = 1_700_000_000_000L,
        )
        val second = writer.write(
            snapshot = snapshot,
            timestamp = 1_700_000_000_000L,
        )

        assertEquals("application/json", result.mimeType)
        assertTrue(result.fileName.startsWith("money-export-"))
        assertTrue(result.fileName.endsWith(".json"))
        assertTrue(result.fileName.matches(Regex("money-export-\\d{8}-\\d{6}-\\d{3}-[0-9a-f]{12}\\.json")))
        assertNotEquals(result.fileName, second.fileName)
        assertEquals("content", result.uri.scheme)
        assertEquals("${context.packageName}.fileprovider", result.uri.authority)

        val exportedText = context.contentResolver
            .openInputStream(result.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals(BackupJsonCodec.encode(snapshot), exportedText)
    }

    private fun emptySnapshot(): MoneyBackupSnapshot = MoneyBackupSnapshot(
        metadata = BackupMetadata(MONEY_BACKUP_SCHEMA_VERSION, 14, 1L),
        portableSettings = BackupPortableSettings("¥", "red_income_green_expense"),
        accounts = emptyList(),
        cashFlowRecords = emptyList(),
        transferRecords = emptyList(),
        balanceUpdateRecords = emptyList(),
        balanceAdjustmentRecords = emptyList(),
        recurringReminders = emptyList(),
        accountReminderConfigs = emptyList(),
    )
}
