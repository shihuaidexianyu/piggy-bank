package com.shihuaidexianyu.money.data.backup

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class StagedBackupHandle(
    val id: String,
    val sha256: String,
    val sizeBytes: Long,
    val stagedAt: Long,
)

class StagedBackupStore(
    cacheDir: File,
    private val maxBytes: Long = MAX_BYTES,
    private val idGenerator: () -> String = ::secureStageId,
) {
    private val directory = File(cacheDir, DIRECTORY_NAME)

    fun stage(input: InputStream, now: Long): StagedBackupHandle {
        require(now > 0L) { "stagedAt 必须大于 0" }
        directory.mkdirs()
        require(directory.isDirectory) { "无法创建导入暂存目录" }
        val id = idGenerator().also(::requireSafeId)
        val part = File(directory, "$id.json.part")
        val destination = dataFile(id)
        val metadataDestination = metadataFile(id)
        check(!part.exists() && !destination.exists() && !metadataDestination.exists()) { "暂存标识冲突" }
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied = Math.addExact(copied, count.toLong())
                    require(copied <= maxBytes) { "备份文件超过 32 MiB" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            require(copied > 0L) { "备份文件为空" }
            require(part.renameTo(destination)) { "无法完成导入文件暂存" }
            val handle = StagedBackupHandle(
                id = id,
                sha256 = digest.digest().toHex(),
                sizeBytes = copied,
                stagedAt = now,
            )
            atomicWrite(metadataDestination, BackupJsonCodec.json.encodeToString(handle).encodeToByteArray())
            return handle
        } catch (error: Throwable) {
            part.delete()
            destination.delete()
            metadataDestination.delete()
            throw error
        }
    }

    fun readVerified(id: String): ByteArray {
        requireSafeId(id)
        val handle = readHandle(id)
        val file = dataFile(id)
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "暂存备份不存在或不是普通文件" }
        require(file.length() == handle.sizeBytes) { "暂存备份长度已改变" }
        require(handle.sizeBytes in 1..maxBytes) { "暂存备份大小无效" }
        val bytes = file.readBytes()
        require(bytes.size.toLong() == handle.sizeBytes) { "暂存备份读取不完整" }
        require(MessageDigest.getInstance("SHA-256").digest(bytes).toHex() == handle.sha256) {
            "暂存备份 SHA-256 不匹配"
        }
        return bytes
    }

    fun get(id: String): StagedBackupHandle = readHandle(id)

    fun list(): List<StagedBackupHandle> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.name.endsWith(METADATA_SUFFIX) }.orEmpty().mapNotNull { file ->
            runCatching { BackupJsonCodec.json.decodeFromString<StagedBackupHandle>(file.readText()) }.getOrNull()
        }.sortedByDescending { it.stagedAt }
    }

    fun exists(id: String): Boolean = runCatching {
        readHandle(id)
        dataFile(id).isFile
    }.getOrDefault(false)

    fun delete(id: String) {
        requireSafeId(id)
        dataFile(id).delete()
        metadataFile(id).delete()
        File(directory, "$id.json.part").delete()
    }

    fun cleanupExpired(now: Long) {
        val cutoff = now - RETENTION_MILLIS
        list().filter { it.stagedAt <= cutoff }.forEach { delete(it.id) }
        directory.listFiles { file -> file.name.endsWith(".part") }.orEmpty().forEach { file ->
            if (file.lastModified() <= cutoff) file.delete()
        }
        directory.listFiles { file ->
            file.name.endsWith(".json") && !file.name.endsWith(METADATA_SUFFIX)
        }.orEmpty().forEach { file ->
            val id = file.name.removeSuffix(".json")
            if (!metadataFile(id).isFile && file.lastModified() <= cutoff) file.delete()
        }
    }

    private fun readHandle(id: String): StagedBackupHandle {
        val file = metadataFile(id)
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "暂存备份元数据不存在" }
        val handle = runCatching {
            BackupJsonCodec.json.decodeFromString<StagedBackupHandle>(file.readText(Charsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("暂存备份元数据损坏", it) }
        require(handle.id == id) { "暂存备份标识不匹配" }
        require(handle.sha256.matches(Regex("[0-9a-f]{64}"))) { "暂存备份 SHA-256 无效" }
        return handle
    }

    private fun dataFile(id: String) = File(directory, "$id.json")
    private fun metadataFile(id: String) = File(directory, "$id$METADATA_SUFFIX")

    companion object {
        const val MAX_BYTES = 32L * 1024L * 1024L
        const val RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
        private const val DIRECTORY_NAME = "imports"
        private const val METADATA_SUFFIX = ".meta.json"
    }
}

private fun requireSafeId(id: String) {
    require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "暂存备份标识无效" }
}

internal fun atomicWrite(
    destination: File,
    bytes: ByteArray,
    moveOperation: (Path, Path) -> Unit = ::replaceAtomically,
) {
    val part = File(destination.parentFile, "${destination.name}.part")
    try {
        FileOutputStream(part).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        moveOperation(part.toPath(), destination.toPath())
    } finally {
        part.delete()
    }
}

private fun replaceAtomically(source: Path, destination: Path) {
    try {
        Files.move(
            source,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun secureStageId(): String {
    val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
    return bytes.toHex()
}
