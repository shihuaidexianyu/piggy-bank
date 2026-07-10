package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.BackupContentHasher
import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.ImportReceiptCounts
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceiptStatus
import com.shihuaidexianyu.money.data.backup.ImportReceiptStore
import com.shihuaidexianyu.money.data.backup.SafetySnapshotStore
import com.shihuaidexianyu.money.data.backup.atomicWrite
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportReceiptStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `pending receipt survives recreation and can be committed atomically`() {
        val store = ImportReceiptStore(temporaryFolder.root)
        store.put(receipt(status = ImportReceiptStatus.PENDING))

        val recreated = ImportReceiptStore(temporaryFolder.root)
        assertEquals("receipt-1", recreated.pending().single().id)

        recreated.markCommitted("receipt-1")

        assertTrue(ImportReceiptStore(temporaryFolder.root).pending().isEmpty())
        assertEquals("receipt-1", ImportReceiptStore(temporaryFolder.root).history().single().id)
    }

    @Test
    fun `failed index replacement leaves previous bytes intact`() {
        val destination = File(temporaryFolder.root, "index.json").apply { writeText("old") }

        assertFailsWith<IllegalStateException> {
            atomicWrite(destination, "new".encodeToByteArray()) { _, _ -> error("injected move failure") }
        }

        assertEquals("old", destination.readText())
    }

    @Test
    fun `verified safety snapshot detects byte changes`() {
        val store = SafetySnapshotStore(temporaryFolder.root, idGenerator = { "safe-a" })
        val stored = store.writeVerified("{\"safe\":true}", now = 100L)

        assertEquals("{\"safe\":true}", store.readVerified(stored.fileName, stored.sha256))
        File(temporaryFolder.root, "pre_import_backups/${stored.fileName}").appendText("x")
        assertFailsWith<IllegalArgumentException> { store.readVerified(stored.fileName, stored.sha256) }
    }

    @Test
    fun `safety retention keeps newest five under thirty days plus protected pending file`() {
        var sequence = 0
        val store = SafetySnapshotStore(temporaryFolder.root, idGenerator = { "safe-${sequence++}" })
        val day = 24L * 60L * 60L * 1_000L
        val old = store.writeVerified("old", now = day)
        val recent = (1..7).map { index -> store.writeVerified("recent-$index", now = 40L * day + index) }

        store.prune(now = 40L * day + 10L, protectedFileNames = setOf(old.fileName))

        assertEquals(5, store.list().size)
        assertTrue(store.list().any { it.fileName == old.fileName })
        assertEquals(recent.takeLast(4).map { it.fileName }.toSet(), store.list().map { it.fileName }.toSet() - old.fileName)
    }

    @Test
    fun `canonical content hash ignores export metadata derived activity and ordering`() {
        val snapshot = fixtureV4()
        val changed = snapshot.copy(
            metadata = snapshot.metadata.copy(exportedAt = snapshot.metadata.exportedAt + 1, appVersionName = "other"),
            accounts = snapshot.accounts.reversed().map { it.copy(lastUsedAt = null, lastBalanceUpdateAt = null) },
            cashFlowRecords = snapshot.cashFlowRecords.reversed(),
        )

        assertEquals(BackupContentHasher.sha256(snapshot), BackupContentHasher.sha256(changed))
    }

    private fun receipt(status: ImportReceiptStatus) = ImportReceipt(
        id = "receipt-1",
        kind = ImportReceiptKind.IMPORT,
        status = status,
        importedAt = 100L,
        sourceFileSha256 = "a".repeat(64),
        targetContentSha256 = "b".repeat(64),
        safetySnapshotFileName = "money-pre-import-100-safe.json",
        safetySnapshotSha256 = "c".repeat(64),
        schemaVersion = 4,
        counts = ImportReceiptCounts(1, 2, 3, 4, 5, 6, 1),
    )

    private fun fixtureV4() = BackupJsonCodec.decode(
        requireNotNull(javaClass.getResource("/backups/v4.json")).readText(),
    )
}
