package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun `v1 fixture migrates sequentially and preserves exact legacy ledger values`() {
        val snapshot = BackupJsonCodec.decode(fixture("v1.json"))

        assertEquals(MONEY_BACKUP_SCHEMA_VERSION, snapshot.metadata.schemaVersion)
        assertEquals(-100L, snapshot.accounts.single { it.id == 1L }.initialBalance)
        assertEquals(1_000L, snapshot.accounts.single { it.id == 1L }.closedAt)
        assertEquals("一段很长的旧用途备注", snapshot.cashFlowRecords.single().note)
        assertEquals(600L, snapshot.cashFlowRecords.single().deletedAt)
        assertEquals("cash:legacy-backup:10", snapshot.cashFlowRecords.single().operationId)
        assertEquals(listOf(41L), snapshot.balanceAdjustmentRecords.map { it.id })
        assertEquals(-5L, snapshot.balanceAdjustmentRecords.single().delta)
        assertEquals("balance-adjustment:legacy-backup:41", snapshot.balanceAdjustmentRecords.single().operationId)
        assertEquals(snapshot.recurringReminders.single().nextDueAt, snapshot.recurringReminders.single().anchorDueAt)
        assertFalse(snapshot.accountReminderConfigs.single { it.accountId == 1L }.config.isEnabled)
        assertNull(snapshot.savingsGoal)
    }

    @Test
    fun `v1 migration fills a default reminder config for every account and remains importable`() {
        val snapshot = BackupJsonCodec.decode(fixture("v1.json"))

        assertEquals(snapshot.accounts.map { it.id }.toSet(), snapshot.accountReminderConfigs.map { it.accountId }.toSet())
        assertTrue(snapshot.accountReminderConfigs.single { it.accountId == 2L }.config.isEnabled)
        ValidateBackupSnapshotUseCase { 1_800_000_000_000L }(snapshot)
    }

    @Test
    fun `v2 fixture receives deterministic operation metadata`() {
        val first = BackupJsonCodec.decode(fixture("v2.json"))
        val second = BackupJsonCodec.decode(fixture("v2.json"))

        assertEquals(first, second)
        assertEquals("balance-adjustment:legacy-backup:1", first.balanceAdjustmentRecords.single().operationId)
        assertEquals(200L, first.balanceAdjustmentRecords.single().updatedAt)
        assertNull(first.balanceAdjustmentRecords.single().deletedAt)
    }

    @Test
    fun `v3 fixture discards device fields and reduces smallest goal to singleton`() {
        val snapshot = BackupJsonCodec.decode(fixture("v3.json"))
        val encoded = BackupJsonCodec.encode(snapshot)

        assertEquals("€", snapshot.portableSettings.currencySymbol)
        assertEquals(1L, snapshot.savingsGoal?.id)
        assertEquals(2_000L, snapshot.savingsGoal?.targetAmount)
        assertEquals(200L, snapshot.savingsGoal?.updatedAt)
        assertEquals(300L, snapshot.accounts.single().closedAt)
        assertEquals("保留 purpose 原文", snapshot.cashFlowRecords.single().note)
        assertFalse(encoded.contains("themeMode"))
        assertFalse(encoded.contains("lastHistoryKeyword"))
        assertFalse(encoded.contains("\"lastNotified"))
        assertFalse(encoded.contains("\"isArchived\""))
        assertFalse(encoded.contains("\"purpose\":"))
    }

    @Test
    fun `v4 fixture round trips all portable fields and ledger metadata`() {
        val snapshot = BackupJsonCodec.decode(fixture("v4.json"))

        assertEquals(snapshot, BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot)))
        assertEquals(500_000L, snapshot.portableSettings.monthlyBudgetAmount)
        assertTrue(snapshot.accounts.single().isHidden)
        assertEquals("cash:v4:1", snapshot.cashFlowRecords.single().operationId)
        assertEquals(300L, snapshot.cashFlowRecords.single().deletedAt)
        assertEquals(50L, snapshot.balanceUpdateRecords.single().delta)
        assertFalse(snapshot.accountReminderConfigs.single().config.isEnabled)
    }

    @Test
    fun `all legacy fixtures produce snapshots accepted by the v4 import validator`() {
        val validator = ValidateBackupSnapshotUseCase { 1_800_000_000_000L }

        listOf("v1.json", "v2.json", "v3.json").forEach { name ->
            validator(BackupJsonCodec.decode(fixture(name)))
        }
    }

    @Test
    fun `future schema is rejected instead of being decoded optimistically`() {
        val future = fixture("v4.json").replace("\"schemaVersion\":4", "\"schemaVersion\":5")

        val error = assertFailsWith<IllegalArgumentException> { BackupJsonCodec.decode(future) }

        assertTrue(error.message.orEmpty().contains("不支持的备份版本"))
    }

    @Test
    fun `missing zero and unknown schema versions are rejected`() {
        val v4 = fixture("v4.json")
        listOf(
            v4.replace("\"schemaVersion\":4,", ""),
            v4.replace("\"schemaVersion\":4", "\"schemaVersion\":0"),
            v4.replace("\"schemaVersion\":4", "\"schemaVersion\":99"),
            fixture("v3.json").replace("\"schemaVersion\":3", "\"schemaVersion\":\"3\""),
        ).forEach { raw ->
            assertFailsWith<IllegalArgumentException> { BackupJsonCodec.decode(raw) }
        }
    }

    @Test
    fun `legacy backups with missing required collections are rejected instead of becoming empty ledgers`() {
        listOf("v1.json", "v2.json", "v3.json").forEach { name ->
            val malformed = fixture(name).replace(Regex("\\s*\\\"cashFlowRecords\\\"\\s*:\\s*\\[[^]]*],?"), "")

            assertFailsWith<IllegalArgumentException>(name) { BackupJsonCodec.decode(malformed) }
        }
    }

    @Test
    fun `legacy backups with malformed required scalar fields are rejected`() {
        val invalidArchived = fixture("v1.json").replace("\"isArchived\":true", "\"isArchived\":\"yes\"")
        val missingRecordId = fixture("v2.json").replace("{\"id\":1,\"accountId\":1,\"delta\":-10", "{\"accountId\":1,\"delta\":-10")

        assertFailsWith<IllegalArgumentException> { BackupJsonCodec.decode(invalidArchived) }
        assertFailsWith<IllegalArgumentException> { BackupJsonCodec.decode(missingRecordId) }
    }

    @Test
    fun `legacy migration preserves contradictory update time for validator rejection`() {
        val malformed = fixture("v3.json").replace("\"updatedAt\":400", "\"updatedAt\":200")

        val snapshot = BackupJsonCodec.decode(malformed)

        assertEquals(200L, snapshot.cashFlowRecords.single().updatedAt)
        assertFailsWith<IllegalArgumentException> {
            ValidateBackupSnapshotUseCase { 1_800_000_000_000L }(snapshot)
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(resource("/backups/$name")).bufferedReader().use { it.readText() }

    private fun resource(path: String): InputStream? = javaClass.getResourceAsStream(path)
}
