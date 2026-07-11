package com.shihuaidexianyu.money.data.backup

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class ImportReceiptKind { IMPORT, ROLLBACK }

@Serializable
enum class ImportReceiptStatus { PENDING, COMMITTED }

@Serializable
data class ImportReceiptCounts(
    val accountCount: Int,
    val cashFlowCount: Int,
    val transferCount: Int,
    val balanceUpdateCount: Int,
    val balanceAdjustmentCount: Int,
    val reminderCount: Int,
    val savingsGoalCount: Int,
)

@Serializable
data class ImportReceipt(
    val id: String,
    val kind: ImportReceiptKind,
    val status: ImportReceiptStatus,
    val importedAt: Long,
    val sourceFileSha256: String,
    val targetContentSha256: String,
    val safetySnapshotFileName: String,
    val safetySnapshotSha256: String,
    val schemaVersion: Int,
    val counts: ImportReceiptCounts,
    val rolledBackReceiptId: String? = null,
    val commitSequence: Long = 0L,
)

@Serializable
private data class ReceiptIndex(
    val receipts: List<ImportReceipt> = emptyList(),
)

class ImportReceiptStore(
    filesDir: File,
) {
    private val directory = File(filesDir, DIRECTORY_NAME)
    private val indexFile = File(directory, INDEX_FILE_NAME)

    @Synchronized
    fun put(receipt: ImportReceipt) {
        val current = readAll()
        val prepared = if (receipt.status == ImportReceiptStatus.COMMITTED && receipt.commitSequence == 0L) {
            receipt.copy(commitSequence = nextCommitSequence(current))
        } else {
            receipt
        }
        validate(prepared)
        val receipts = current.filterNot { it.id == prepared.id } + prepared
        validateIndex(receipts)
        writeAll(receipts)
    }

    @Synchronized
    fun markCommitted(id: String): ImportReceipt {
        val all = readAll()
        val existing = requireNotNull(all.firstOrNull { it.id == id }) { "导入收据不存在" }
        if (existing.status == ImportReceiptStatus.COMMITTED) return existing
        val committed = existing.copy(
            status = ImportReceiptStatus.COMMITTED,
            commitSequence = nextCommitSequence(all),
        )
        validate(committed)
        writeAll(all.map { if (it.id == id) committed else it })
        return committed
    }

    @Synchronized
    fun discardPending(id: String) {
        writeAll(readAll().filterNot { it.id == id && it.status == ImportReceiptStatus.PENDING })
    }

    @Synchronized
    fun pending(): List<ImportReceipt> = readAll()
        .filter { it.status == ImportReceiptStatus.PENDING }
        .sortedByDescending { it.importedAt }

    @Synchronized
    fun history(): List<ImportReceipt> = readAll()
        .filter { it.status == ImportReceiptStatus.COMMITTED }
        .sortedByDescending { it.commitSequence }

    @Synchronized
    fun findCommitted(id: String): ImportReceipt? = history().firstOrNull { it.id == id }

    @Synchronized
    fun find(id: String): ImportReceipt? = readAll().firstOrNull { it.id == id }

    private fun readAll(): List<ImportReceipt> {
        if (!indexFile.isFile) return emptyList()
        return try {
            val decoded = BackupJsonCodec.json
                .decodeFromString<ReceiptIndex>(indexFile.readText(Charsets.UTF_8))
                .receipts
            val migrated = migrateLegacyCommitSequence(decoded)
            validateIndex(migrated)
            if (migrated != decoded) writeAll(migrated)
            migrated
        } catch (error: Exception) {
            throw IllegalStateException("导入收据索引损坏：${error.message ?: "未知错误"}", error)
        }
    }

    private fun writeAll(receipts: List<ImportReceipt>) {
        directory.mkdirs()
        require(directory.isDirectory) { "无法创建导入收据目录" }
        atomicWrite(indexFile, BackupJsonCodec.json.encodeToString(ReceiptIndex(receipts)).encodeToByteArray())
    }

    private fun validate(receipt: ImportReceipt) {
        require(receipt.id.matches(SAFE_ID)) { "导入收据标识无效" }
        require(receipt.importedAt > 0L) { "导入时间无效" }
        require(receipt.sourceFileSha256.matches(SHA_256)) { "来源 SHA-256 无效" }
        require(receipt.targetContentSha256.matches(SHA_256)) { "目标内容 SHA-256 无效" }
        require(receipt.safetySnapshotSha256.matches(SHA_256)) { "安全快照 SHA-256 无效" }
        require(receipt.safetySnapshotFileName.matches(SAFE_FILE)) { "安全快照文件名无效" }
        require(receipt.commitSequence >= 0L) { "导入收据提交顺序无效" }
        require(receipt.status != ImportReceiptStatus.PENDING || receipt.commitSequence == 0L) {
            "待提交收据不能包含提交顺序"
        }
        require(receipt.status != ImportReceiptStatus.COMMITTED || receipt.commitSequence > 0L) {
            "已提交收据缺少提交顺序"
        }
    }

    private fun migrateLegacyCommitSequence(receipts: List<ImportReceipt>): List<ImportReceipt> {
        val committed = receipts.filter { it.status == ImportReceiptStatus.COMMITTED }
        val hasLegacy = committed.any { it.commitSequence == 0L }
        val hasSequenced = committed.any { it.commitSequence > 0L }
        require(!(hasLegacy && hasSequenced)) { "导入收据提交顺序损坏：新旧格式混用" }
        if (!hasLegacy) return receipts
        var sequence = 0L
        return receipts.map { receipt ->
            if (receipt.status == ImportReceiptStatus.COMMITTED) {
                sequence = Math.addExact(sequence, 1L)
                receipt.copy(commitSequence = sequence)
            } else {
                receipt
            }
        }
    }

    private fun validateIndex(receipts: List<ImportReceipt>) {
        receipts.forEach(::validate)
        val committedSequences = receipts
            .filter { it.status == ImportReceiptStatus.COMMITTED }
            .map(ImportReceipt::commitSequence)
        require(committedSequences.size == committedSequences.toSet().size) {
            "导入收据提交顺序损坏：存在重复序号"
        }
    }

    private fun nextCommitSequence(receipts: List<ImportReceipt>): Long {
        val current = receipts.maxOfOrNull(ImportReceipt::commitSequence) ?: 0L
        return try {
            Math.addExact(current, 1L)
        } catch (error: ArithmeticException) {
            throw IllegalStateException("导入收据提交顺序已耗尽", error)
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "import_receipts"
        const val INDEX_FILE_NAME = "receipts.json"
        val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
        val SHA_256 = Regex("[0-9a-f]{64}")
        val SAFE_FILE = Regex("money-pre-import-[0-9]+-[A-Za-z0-9_-]+\\.json")
    }
}

@Serializable
data class StoredSafetySnapshot(
    val fileName: String,
    val sha256: String,
    val createdAt: Long,
)

class SafetySnapshotStore(
    filesDir: File,
    private val idGenerator: () -> String = ::secureReceiptId,
) {
    private val directory = File(filesDir, DIRECTORY_NAME)

    fun writeVerified(json: String, now: Long): StoredSafetySnapshot {
        require(now > 0L) { "安全快照时间无效" }
        directory.mkdirs()
        require(directory.isDirectory) { "无法创建安全快照目录" }
        val id = idGenerator().also { require(it.matches(SAFE_ID)) { "安全快照标识无效" } }
        val fileName = "money-pre-import-$now-$id.json"
        val file = File(directory, fileName)
        atomicWrite(file, json.encodeToByteArray())
        file.setLastModified(now)
        val bytes = file.readBytes()
        require(bytes.contentEquals(json.encodeToByteArray())) { "安全快照复读不一致" }
        val sha256 = sha256(bytes)
        require(readVerified(fileName, sha256) == json) { "安全快照校验失败" }
        return StoredSafetySnapshot(fileName, sha256, now)
    }

    fun readVerified(fileName: String, expectedSha256: String): String {
        require(fileName.matches(SAFE_FILE)) { "安全快照文件名无效" }
        require(expectedSha256.matches(SHA_256)) { "安全快照 SHA-256 无效" }
        val file = File(directory, fileName)
        require(file.isFile) { "安全快照不存在" }
        val bytes = file.readBytes()
        require(sha256(bytes) == expectedSha256) { "安全快照 SHA-256 不匹配" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun list(): List<StoredSafetySnapshot> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.name.matches(SAFE_FILE) }.orEmpty().map { file ->
            StoredSafetySnapshot(
                fileName = file.name,
                sha256 = sha256(file.readBytes()),
                createdAt = parseTimestamp(file.name),
            )
        }.sortedByDescending { it.createdAt }
    }

    fun prune(now: Long, protectedFileNames: Set<String>) {
        val cutoff = now - RETENTION_MILLIS
        val snapshots = list()
        val protected = snapshots.filter { it.fileName in protectedFileNames }.map { it.fileName }.toSet()
        val candidates = snapshots.filterNot { it.fileName in protected }
        val remainingSlots = (MAX_SNAPSHOTS - protected.size).coerceAtLeast(0)
        val keep = candidates.filter { it.createdAt > cutoff }.take(remainingSlots).map { it.fileName }.toSet()
        candidates.filterNot { it.fileName in keep }.forEach { File(directory, it.fileName).delete() }
    }

    private fun parseTimestamp(fileName: String): Long =
        SAFE_FILE.matchEntire(fileName)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw IllegalArgumentException("安全快照文件名无效")

    companion object {
        const val MAX_SNAPSHOTS = 5
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        private const val DIRECTORY_NAME = "pre_import_backups"
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
        private val SAFE_FILE = Regex("money-pre-import-([0-9]+)-[A-Za-z0-9_-]+\\.json")
        private val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun secureReceiptId(): String = ByteArray(16).also(SecureRandom()::nextBytes).toHex()
