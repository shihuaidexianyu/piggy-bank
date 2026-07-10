package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import java.io.InputStream
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester

fun interface CurrentBackupSnapshotSource {
    suspend fun build(exportedAt: Long): MoneyBackupSnapshot
}

data class StagedImportPreview(
    val stageId: String,
    val sourceFileSha256: String,
    val sourceSizeBytes: Long,
    val validation: BackupValidationResult,
)

class BackupImportCoordinator(
    private val stagedStore: StagedBackupStore,
    private val safetyStore: SafetySnapshotStore,
    private val receiptStore: ImportReceiptStore,
    private val currentSnapshotSource: CurrentBackupSnapshotSource,
    private val backupRepository: BackupRepository,
    private val validator: ValidateBackupSnapshotUseCase,
    private val clockProvider: ClockProvider,
    private val receiptIdGenerator: () -> String = ::secureReceiptId,
    private val markReceiptCommitted: (String) -> ImportReceipt = receiptStore::markCommitted,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
) {
    fun stage(input: InputStream): StagedBackupHandle {
        stagedStore.cleanupExpired(clockProvider.nowMillis())
        return stagedStore.stage(input, clockProvider.nowMillis())
    }

    fun stageExists(stageId: String): Boolean = stagedStore.exists(stageId)

    fun preview(stageId: String): StagedImportPreview {
        stagedStore.cleanupExpired(clockProvider.nowMillis())
        val handle = stagedStore.get(stageId)
        val snapshot = decodeStaged(stageId)
        val validation = validator(snapshot)
        return StagedImportPreview(
            stageId = stageId,
            sourceFileSha256 = handle.sha256,
            sourceSizeBytes = handle.sizeBytes,
            validation = validation,
        )
    }

    suspend fun confirm(stageId: String): ImportReceipt {
        stagedStore.cleanupExpired(clockProvider.nowMillis())
        val handle = stagedStore.get(stageId)
        val target = decodeStaged(stageId)
        val result = replaceValidated(
            target = target,
            sourceFileSha256 = handle.sha256,
            kind = ImportReceiptKind.IMPORT,
            rolledBackReceiptId = null,
        )
        stagedStore.delete(stageId)
        requestSync(NotificationSyncReason.IMPORT)
        return result
    }

    suspend fun rollback(receiptId: String): ImportReceipt {
        val receipt = requireNotNull(receiptStore.find(receiptId)) { "找不到可撤销的导入记录" }
        val raw = safetyStore.readVerified(receipt.safetySnapshotFileName, receipt.safetySnapshotSha256)
        val target = BackupJsonCodec.decode(raw)
        validator(target)
        val result = replaceValidated(
            target = target,
            sourceFileSha256 = receipt.safetySnapshotSha256,
            kind = ImportReceiptKind.ROLLBACK,
            rolledBackReceiptId = receipt.id,
            expectedCurrentContentSha256 = receipt.targetContentSha256,
        )
        requestSync(NotificationSyncReason.ROLLBACK)
        return result
    }

    fun history(): List<ImportReceipt> = receiptStore.history()

    suspend fun recoverPendingReceipts() {
        val recoveredSafetySnapshots = mutableSetOf<String>()
        receiptStore.pending().forEach { pending ->
            val current = currentSnapshotSource.build(clockProvider.nowMillis())
            if (BackupContentHasher.sha256(current) == pending.targetContentSha256) {
                safetyStore.readVerified(pending.safetySnapshotFileName, pending.safetySnapshotSha256)
                markReceiptCommitted(pending.id)
                recoveredSafetySnapshots += pending.safetySnapshotFileName
            } else {
                receiptStore.discardPending(pending.id)
            }
        }
        pruneRetention(recoveredSafetySnapshots)
    }

    fun cleanupExpiredStages() {
        stagedStore.cleanupExpired(clockProvider.nowMillis())
    }

    private fun decodeStaged(stageId: String): MoneyBackupSnapshot {
        val bytes = stagedStore.readVerified(stageId)
        return runCatching { BackupJsonCodec.decode(bytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("备份 JSON 无法解析或迁移", it) }
    }

    private suspend fun replaceValidated(
        target: MoneyBackupSnapshot,
        sourceFileSha256: String,
        kind: ImportReceiptKind,
        rolledBackReceiptId: String?,
        expectedCurrentContentSha256: String? = null,
    ): ImportReceipt {
        val targetValidation = validator(target)
        val normalizedTarget = BackupSnapshotNormalizer.normalizeForPersistence(target)
        validator(normalizedTarget)
        val now = clockProvider.nowMillis()
        val current = BackupSnapshotNormalizer.normalizeForPersistence(currentSnapshotSource.build(now))
        validator(current)
        val currentContentSha256 = BackupContentHasher.sha256(current)
        require(expectedCurrentContentSha256 == null || currentContentSha256 == expectedCurrentContentSha256) {
            "导入后账本已发生变化，不能直接撤销"
        }

        val safety = safetyStore.writeVerified(BackupJsonCodec.encode(current), now)
        val verifiedSafety = BackupJsonCodec.decode(
            safetyStore.readVerified(safety.fileName, safety.sha256),
        )
        validator(verifiedSafety)
        require(BackupContentHasher.sha256(verifiedSafety) == currentContentSha256) {
            "安全快照内容复验失败"
        }

        val pending = ImportReceipt(
            id = receiptIdGenerator(),
            kind = kind,
            status = ImportReceiptStatus.PENDING,
            importedAt = now,
            sourceFileSha256 = sourceFileSha256,
            targetContentSha256 = BackupContentHasher.sha256(normalizedTarget),
            safetySnapshotFileName = safety.fileName,
            safetySnapshotSha256 = safety.sha256,
            schemaVersion = normalizedTarget.metadata.schemaVersion,
            counts = targetValidation.toReceiptCounts(),
            rolledBackReceiptId = rolledBackReceiptId,
        )
        receiptStore.put(pending)
        var databaseCommitted = false
        try {
            backupRepository.replaceAllIfUnchanged(normalizedTarget, currentContentSha256)
            databaseCommitted = true
            val committed = runCatching { markReceiptCommitted(pending.id) }.getOrElse { pending }
            runCatching { pruneRetention(setOf(committed.safetySnapshotFileName)) }
            return committed
        } catch (error: Throwable) {
            if (!databaseCommitted) receiptStore.discardPending(pending.id)
            throw error
        }
    }

    private fun pruneRetention(additionalProtected: Set<String> = emptySet()) {
        val protected = receiptStore.pending().mapTo(additionalProtected.toMutableSet()) {
            it.safetySnapshotFileName
        }
        safetyStore.prune(clockProvider.nowMillis(), protected)
    }

    private fun requestSync(reason: NotificationSyncReason) {
        runCatching { notificationSyncRequester.request(reason) }
    }
}

private fun BackupValidationResult.toReceiptCounts() = ImportReceiptCounts(
    accountCount,
    cashFlowCount,
    transferCount,
    balanceUpdateCount,
    balanceAdjustmentCount,
    reminderCount,
    savingsGoalCount,
)
