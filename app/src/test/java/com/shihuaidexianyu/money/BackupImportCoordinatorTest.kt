package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.BackupContentHasher
import com.shihuaidexianyu.money.data.backup.BackupImportCoordinator
import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.backup.ImportReceiptCounts
import com.shihuaidexianyu.money.data.backup.ImportReceiptKind
import com.shihuaidexianyu.money.data.backup.ImportReceiptStatus
import com.shihuaidexianyu.money.data.backup.ImportReceiptStore
import com.shihuaidexianyu.money.data.backup.SafetySnapshotStore
import com.shihuaidexianyu.money.data.backup.StagedBackupStore
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupImportCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `confirm uses staged bytes persists receipt and normalizes stale activity`() = runBlocking {
        val original = fixtureV4()
        val target = original.copy(
            accounts = original.accounts.map { it.copy(lastUsedAt = 123L, lastBalanceUpdateAt = null) },
            portableSettings = original.portableSettings.copy(currencySymbol = "$"),
        )
        val repository = FakeBackupRepository(original)
        val coordinator = coordinator(repository)
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))

        val preview = coordinator.preview(stage.id)
        val receipt = coordinator.confirm(stage.id)

        assertEquals(stage.sha256, preview.sourceFileSha256)
        assertEquals(ImportReceiptStatus.COMMITTED, receipt.status)
        assertEquals("$", repository.current.portableSettings.currencySymbol)
        assertEquals(500L, repository.current.accounts.single().lastUsedAt)
        assertEquals(400L, repository.current.accounts.single().lastBalanceUpdateAt)
        assertEquals(receipt, coordinator.history().single())
        assertTrue(!coordinator.stageExists(stage.id))
    }

    @Test
    fun `mutation between safety snapshot and replace aborts without overwriting mutation`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original).apply {
            beforeCompare = {
                current = current.copy(portableSettings = current.portableSettings.copy(currencySymbol = "€"))
            }
        }
        val coordinator = coordinator(repository)
        val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))

        assertFailsWith<IllegalArgumentException> { coordinator.confirm(stage.id) }

        assertEquals("€", repository.current.portableSettings.currencySymbol)
        assertTrue(coordinator.history().isEmpty())
    }

    @Test
    fun `pending crash recovery distinguishes before and after database commit by canonical target hash`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original)
        val receiptStore = ImportReceiptStore(temporaryFolder.root)
        val safety = SafetySnapshotStore(temporaryFolder.root, idGenerator = { "recovery" })
            .writeVerified(BackupJsonCodec.encode(original), NOW)
        val before = pendingReceipt("before", targetHash = "f".repeat(64), safety.fileName, safety.sha256)
        val after = pendingReceipt(
            "after",
            targetHash = BackupContentHasher.sha256(original),
            safety.fileName,
            safety.sha256,
        )
        receiptStore.put(before)
        receiptStore.put(after)

        coordinator(repository, receiptStore).recoverPendingReceipts()

        assertTrue(receiptStore.pending().isEmpty())
        assertEquals(listOf("after"), receiptStore.history().map { it.id })
    }

    @Test
    fun `rollback reuses replacement pipeline and creates its own protection receipt repeatedly`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original)
        val coordinator = coordinator(repository)
        val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))
        val imported = coordinator.confirm(stage.id)

        val rollbackOne = coordinator.rollback(imported.id)
        val rollbackTwo = coordinator.rollback(rollbackOne.id)

        assertEquals("$", repository.current.portableSettings.currencySymbol)
        assertEquals(ImportReceiptKind.ROLLBACK, rollbackOne.kind)
        assertEquals(ImportReceiptKind.ROLLBACK, rollbackTwo.kind)
        assertEquals(3, coordinator.history().size)
    }

    @Test
    fun `receipt status write failure after database commit is reported as pending success and remains undoable`() =
        runBlocking {
            val original = fixtureV4()
            val repository = FakeBackupRepository(original)
            val receiptStore = ImportReceiptStore(temporaryFolder.root)
            val coordinator = coordinator(repository, receiptStore) { error("injected receipt commit failure") }
            val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
            val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))

            val receipt = coordinator.confirm(stage.id)

            assertEquals(ImportReceiptStatus.PENDING, receipt.status)
            assertEquals("$", repository.current.portableSettings.currencySymbol)
            assertTrue(!coordinator.stageExists(stage.id))
            coordinator.rollback(receipt.id)
            assertEquals("¥", repository.current.portableSettings.currencySymbol)
        }

    @Test
    fun `rollback refuses to overwrite ledger changes made after import`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original)
        val coordinator = coordinator(repository)
        val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))
        val receipt = coordinator.confirm(stage.id)
        repository.current = repository.current.copy(
            portableSettings = repository.current.portableSettings.copy(monthlyBudgetAmount = 123L),
        )

        assertFailsWith<IllegalArgumentException> { coordinator.rollback(receipt.id) }
        assertEquals(123L, repository.current.portableSettings.monthlyBudgetAmount)
    }

    @Test
    fun `rollback CAS rejects a mutation after its current snapshot was captured`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original)
        val coordinator = coordinator(repository)
        val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))
        val receipt = coordinator.confirm(stage.id)
        repository.afterSnapshot = {
            repository.current = repository.current.copy(
                portableSettings = repository.current.portableSettings.copy(monthlyBudgetAmount = 321L),
            )
            repository.afterSnapshot = null
        }

        assertFailsWith<IllegalArgumentException> { coordinator.rollback(receipt.id) }

        assertEquals("$", repository.current.portableSettings.currencySymbol)
        assertEquals(321L, repository.current.portableSettings.monthlyBudgetAmount)
    }

    @Test
    fun `successful import protects its safety snapshot when device clock moved backwards`() = runBlocking {
        val original = fixtureV4()
        val repository = FakeBackupRepository(original)
        val safetyStore = SafetySnapshotStore(
            temporaryFolder.root,
            idGenerator = sequenceOf("future-a", "future-b", "future-c", "future-d", "future-e").iterator()::next,
        )
        repeat(5) { index ->
            safetyStore.writeVerified("future-$index", NOW + 1_000L + index)
        }
        val coordinator = coordinator(repository)
        val target = original.copy(portableSettings = original.portableSettings.copy(currencySymbol = "$"))
        val stage = coordinator.stage(ByteArrayInputStream(BackupJsonCodec.encode(target).encodeToByteArray()))

        val receipt = coordinator.confirm(stage.id)

        safetyStore.readVerified(receipt.safetySnapshotFileName, receipt.safetySnapshotSha256)
        coordinator.rollback(receipt.id)
        assertEquals("¥", repository.current.portableSettings.currencySymbol)
    }

    private fun coordinator(
        repository: FakeBackupRepository,
        receiptStore: ImportReceiptStore = ImportReceiptStore(temporaryFolder.root),
        markCommitted: (String) -> ImportReceipt = receiptStore::markCommitted,
    ) = BackupImportCoordinator(
        stagedStore = StagedBackupStore(temporaryFolder.root),
        safetyStore = SafetySnapshotStore(temporaryFolder.root),
        receiptStore = receiptStore,
        currentSnapshotSource = { exportedAt ->
            val snapshot = repository.current.copy(metadata = repository.current.metadata.copy(exportedAt = exportedAt))
            repository.afterSnapshot?.invoke()
            snapshot
        },
        backupRepository = repository,
        validator = ValidateBackupSnapshotUseCase { NOW },
        clockProvider = { NOW },
        receiptIdGenerator = sequenceOf("receipt-a", "receipt-b", "receipt-c", "receipt-d").iterator()::next,
        markReceiptCommitted = markCommitted,
    )

    private class FakeBackupRepository(
        var current: MoneyBackupSnapshot,
    ) : BackupRepository {
        var beforeCompare: (() -> Unit)? = null
        var afterSnapshot: (() -> Unit)? = null

        override suspend fun replaceAllIfUnchanged(
            snapshot: MoneyBackupSnapshot,
            expectedCurrentContentSha256: String,
        ) {
            beforeCompare?.invoke()
            require(BackupContentHasher.sha256(current) == expectedCurrentContentSha256) { "current changed" }
            current = snapshot
        }
    }

    private fun pendingReceipt(
        id: String,
        targetHash: String,
        safetyFileName: String,
        safetySha256: String,
    ) = ImportReceipt(
        id = id,
        kind = ImportReceiptKind.IMPORT,
        status = ImportReceiptStatus.PENDING,
        importedAt = NOW,
        sourceFileSha256 = "a".repeat(64),
        targetContentSha256 = targetHash,
        safetySnapshotFileName = safetyFileName,
        safetySnapshotSha256 = safetySha256,
        schemaVersion = 4,
        counts = ImportReceiptCounts(1, 1, 0, 1, 1, 1, 1),
    )

    private fun fixtureV4() = BackupJsonCodec.decode(
        requireNotNull(javaClass.getResource("/backups/v4.json")).readText(),
    )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
