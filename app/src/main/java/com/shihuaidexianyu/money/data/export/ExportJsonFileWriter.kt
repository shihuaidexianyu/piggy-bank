package com.shihuaidexianyu.money.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportShareFile(
    val uri: Uri,
    val fileName: String,
    val mimeType: String = "application/json",
)

/**
 * Result of an encrypted export. The caller should persist [recoveryKey] somewhere safe
 * (e.g. password manager) — without it the backup cannot be decrypted.
 */
data class EncryptedExportShareFile(
    val shareFile: ExportShareFile,
    /**
     * Base64-encoded AES-256 key used to encrypt the backup. Display this to the user exactly once
     * and tell them to store it securely. There is no recovery path if lost.
     */
    val recoveryKey: String,
)

class ExportJsonFileWriter(
    private val context: Context,
) {
    /**
     * Writes the JSON as plaintext to `cache/exports/` and returns a shareable FileProvider URI.
     */
    suspend fun write(json: String, timestamp: Long = System.currentTimeMillis()): ExportShareFile {
        return withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            val fileName = buildFileName(timestamp)
            val exportFile = File(exportDir, fileName)
            exportFile.writeText(json, Charsets.UTF_8)
            ExportShareFile(
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile,
                ),
                fileName = fileName,
            )
        }
    }

    /**
     * Writes the JSON as an AES-GCM encrypted blob to `cache/exports/` and returns a shareable URI
     * plus the per-export recovery key. The file format is:
     *
     * ```
     * [12-byte IV][ciphertext+16-byte GCM tag]
     * ```
     *
     * Format is intentionally simple and self-describing so the matching [BackupFileReader] can
     * decrypt without any out-of-band metadata beyond the user-supplied key.
     */
    suspend fun writeEncrypted(json: String, timestamp: Long = System.currentTimeMillis()): EncryptedExportShareFile {
        return withContext(Dispatchers.IO) {
            val secretKey = KeyGenerator.getInstance("AES").apply {
                init(256, SecureRandom())
            }.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, SecureRandom().generateSeed(IV_BYTES)))
            }
            val iv = cipher.iv
            val cipherText = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            val fileName = "${buildFileNameBase(timestamp)}.enc"
            val exportFile = File(exportDir, fileName)
            exportFile.outputStream().use { out ->
                out.write(iv)
                out.write(cipherText)
            }
            EncryptedExportShareFile(
                shareFile = ExportShareFile(
                    uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportFile,
                    ),
                    fileName = fileName,
                    mimeType = "application/octet-stream",
                ),
                recoveryKey = android.util.Base64.encodeToString(secretKey.encoded, android.util.Base64.NO_WRAP),
            )
        }
    }

    /**
     * Decrypts a file produced by [writeEncrypted] using the user-supplied recovery key.
     * Throws [IllegalArgumentException] if the key is malformed; throws a [javax.crypto.AEADBadTagException]
     * if the key is wrong or the file was tampered with.
     */
    suspend fun decryptEncrypted(file: File, recoveryKey: String): String = withContext(Dispatchers.IO) {
        val keyBytes = android.util.Base64.decode(recoveryKey, android.util.Base64.DEFAULT)
        require(keyBytes.size == AES_KEY_BYTES) { "恢复密钥长度不正确（应为 32 字节，AES-256）" }
        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
        val bytes = file.readBytes()
        require(bytes.size > IV_BYTES) { "加密文件已损坏" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipherText = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun buildFileNameBase(timestamp: Long): String {
        val dateText = FILE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),
        )
        return "money-export-$dateText"
    }

    private fun buildFileName(timestamp: Long): String = "${buildFileNameBase(timestamp)}.json"

    private companion object {
        const val EXPORT_DIR_NAME = "exports"
        val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val AES_KEY_BYTES = 32
    }
}

